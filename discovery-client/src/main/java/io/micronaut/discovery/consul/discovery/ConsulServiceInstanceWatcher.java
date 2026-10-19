/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.consul.discovery;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.ConsulServiceInstance;
import io.micronaut.discovery.consul.client.v1.ConsistencyMode;
import io.micronaut.discovery.consul.client.v1.ConsulCheck;
import io.micronaut.discovery.consul.client.v1.ConsulHealthEntry;
import io.micronaut.discovery.consul.client.v1.ConsulServiceEntry;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Keeps the instances of each looked up Consul service up to date with a blocking query on
 * {@code /v1/health/service/{service}} and serves {@link io.micronaut.discovery.DiscoveryClient#getInstances(String)}
 * from that cache. A service is watched from its first lookup until the application context is closed, or until it has
 * not been looked up for {@link ConsulServiceWatchConfiguration#getIdleTimeout()}.
 *
 * <p>While the watch of a service has no result yet the lookup waits for the first query, which is bounded by the read
 * timeout of the Consul client; while the watch is failing
 * lookups query Consul directly. A {@link ServiceInstancesChangedEvent} is published when the instances change.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(beans = ConsulServiceWatchConfiguration.class)
public final class ConsulServiceInstanceWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulServiceInstanceWatcher.class);
    private static final Duration DEFAULT_INITIAL_QUERY_TIMEOUT = Duration.ofSeconds(10);
    // Consul indexes are always greater than zero, see nextIndex
    private static final long NO_INDEX = 0;

    private final ConsulServiceWatchClient client;
    private final ConsulConfiguration consulConfiguration;
    private final ConsulServiceWatchConfiguration watchConfiguration;
    private final ApplicationEventPublisher<ServiceInstancesChangedEvent> eventPublisher;
    private final Scheduler scheduler;
    private final Duration initialQueryTimeout;
    private final Map<String, ServiceWatch> watches = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * @param client              The blocking query client
     * @param consulConfiguration The Consul configuration
     * @param watchConfiguration  The watch configuration
     * @param eventPublisher      The publisher of change events
     * @param executor            The executor that handles query results and publishes events
     */
    ConsulServiceInstanceWatcher(ConsulServiceWatchClient client,
                                 ConsulConfiguration consulConfiguration,
                                 ConsulServiceWatchConfiguration watchConfiguration,
                                 ApplicationEventPublisher<ServiceInstancesChangedEvent> eventPublisher,
                                 @Named(TaskExecutors.BLOCKING) Executor executor) {
        this.client = client;
        this.consulConfiguration = consulConfiguration;
        this.watchConfiguration = watchConfiguration;
        this.eventPublisher = eventPublisher;
        this.scheduler = Schedulers.fromExecutor(executor);
        this.initialQueryTimeout = consulConfiguration.getReadTimeout().orElse(DEFAULT_INITIAL_QUERY_TIMEOUT);
    }

    /**
     * Returns the instances of a service from the watch, starting the watch on the first lookup.
     *
     * @param serviceId   The hyphenated service ID
     * @param directQuery Queries Consul directly, used while the watch is failing
     * @return The instances
     */
    public Publisher<List<ServiceInstance>> getInstances(String serviceId, Supplier<Publisher<List<ServiceInstance>>> directQuery) {
        if (closed) {
            return directQuery.get();
        }
        ServiceWatch watch = watches.computeIfAbsent(serviceId, ServiceWatch::new);
        watch.lastAccess = System.nanoTime();
        watch.start();
        if (closed) {
            stop(watch);
            return directQuery.get();
        }
        if (!watch.failing) {
            List<ServiceInstance> instances = watch.instances();
            if (instances != null) {
                return Mono.just(instances);
            }
            return Mono.fromFuture(watch.firstResult, true)
                .timeout(initialQueryTimeout)
                .onErrorResume(e -> Mono.from(directQuery.get()));
        }
        return directQuery.get();
    }

    /**
     * @return The IDs of the watched services
     */
    public Set<String> getWatchedServiceIds() {
        return Collections.unmodifiableSet(watches.keySet());
    }

    /**
     * @param serviceId The hyphenated service ID
     * @return Whether lookups of the service are served from a healthy watch
     */
    public boolean isWatching(String serviceId) {
        ServiceWatch watch = watches.get(serviceId);
        return watch != null && watch.instances() != null && !watch.failing;
    }

    /**
     * Stops every watch.
     */
    @PreDestroy
    public void close() {
        closed = true;
        for (ServiceWatch watch : watches.values()) {
            stop(watch);
        }
        watches.clear();
    }

    private void stop(ServiceWatch watch) {
        LOG.debug("Stopping the watch of service {}", watch.serviceId);
        watch.dispose();
        watch.firstResult.completeExceptionally(new IllegalStateException("The watch of service " + watch.serviceId + " is stopped"));
    }

    private boolean isIdle(ServiceWatch watch) {
        Duration idleTimeout = watchConfiguration.getIdleTimeout().orElse(null);
        return idleTimeout != null && System.nanoTime() - watch.lastAccess > idleTimeout.toNanos();
    }

    private Mono<HttpResponse<List<ConsulHealthEntry>>> query(ServiceWatch watch) {
        String serviceId = watch.serviceId;
        ConsulConfiguration.ConsulDiscoveryConfiguration discovery = consulConfiguration.getDiscovery();
        List<String> tags = discovery.resolveQueryTags(serviceId);
        String filter = discovery.resolveFilter(serviceId).orElse(null);
        ConsistencyMode mode = discovery.getConsistencyMode();
        long current = watch.index.get();
        Long index = current == NO_INDEX ? null : current;
        LOG.trace("Querying the instances of service {} with index {}", serviceId, index);
        return client.watchHealthService(
            serviceId,
            discovery.isPassing() ? Boolean.TRUE : null,
            tags == null || tags.isEmpty() ? null : tags,
            discovery.getDatacenters().get(serviceId),
            filter == null || filter.isEmpty() ? null : filter,
            mode == ConsistencyMode.CONSISTENT ? Boolean.TRUE : null,
            mode == ConsistencyMode.STALE ? Boolean.TRUE : null,
            index,
            index == null ? null : watchConfiguration.getWait().toMillis() + "ms"
        );
    }

    private void onResponse(ServiceWatch watch, HttpResponse<List<ConsulHealthEntry>> response) {
        if (watch.disposed) {
            return;
        }
        String header = response.getHeaders().get(ConsulServiceWatchClient.HEADER_CONSUL_INDEX);
        watch.index.updateAndGet(current -> nextIndex(current, header));
        List<ConsulHealthEntry> entries = response.getBody().orElse(Collections.emptyList());
        String scheme = consulConfiguration.getDiscovery().getSchemes().getOrDefault(watch.serviceId, "http");
        List<ServiceInstance> newInstances = new ArrayList<>(entries.size());
        Set<InstanceState> newStates = new HashSet<>();
        for (ConsulHealthEntry entry : entries) {
            newInstances.add(new ConsulServiceInstance(entry, scheme));
            newStates.add(InstanceState.of(entry));
        }
        Snapshot snapshot = new Snapshot(List.copyOf(newInstances), Set.copyOf(newStates));
        List<ServiceInstance> instances = snapshot.instances();

        Snapshot previousSnapshot = watch.snapshot.getAndSet(snapshot);
        List<ServiceInstance> previous = previousSnapshot != null ? previousSnapshot.instances() : null;
        boolean changed = previousSnapshot != null && !snapshot.states().equals(previousSnapshot.states());
        if (watch.failing) {
            LOG.info("The watch of service {} recovered", watch.serviceId);
        }
        watch.failing = false;
        watch.errors.set(0);
        watch.firstResult.complete(instances);

        if (changed) {
            LOG.debug("The instances of service {} changed", watch.serviceId);
            try {
                eventPublisher.publishEvent(new ServiceInstancesChangedEvent(watch.serviceId, previous, instances));
            } catch (Exception e) {
                LOG.error("Error publishing the change of the instances of service {}", watch.serviceId, e);
            }
        } else {
            LOG.trace("The instances of service {} did not change", watch.serviceId);
        }
    }

    private void onError(ServiceWatch watch, Throwable e) {
        if (watch.disposed) {
            return;
        }
        // the read timeout exceeds the blocking query wait plus the jitter Consul adds, so an unchanged query
        // returns before it: a timeout means Consul is unresponsive and the cache can no longer be trusted
        if (e instanceof ReadTimeoutException) {
            LOG.debug("Blocking query of service {} timed out", watch.serviceId);
        }
        watch.errors.incrementAndGet();
        if (!watch.failing) {
            LOG.warn("Watching service {} failed, querying Consul directly until the watch recovers: {}", watch.serviceId, e.getMessage());
            LOG.debug("Watch failure of service {}", watch.serviceId, e);
        }
        watch.failing = true;
        // query without an index next, which returns at once, so the cache is trusted again as soon as Consul answers
        // instead of after a blocking query that may wait for a change
        watch.index.set(NO_INDEX);
        watch.firstResult.completeExceptionally(e);
    }

    /**
     * Applies the index rules of https://developer.hashicorp.com/consul/api-docs/features/blocking#implementation-details.
     * The returned index is always greater than zero, or {@link #NO_INDEX} to query without an index.
     */
    private static long nextIndex(long current, @Nullable String header) {
        if (header == null) {
            return NO_INDEX;
        }
        long next;
        try {
            next = Long.parseLong(header.trim());
        } catch (NumberFormatException _) {
            return NO_INDEX;
        }
        if (next <= 0) {
            return 1L;
        }
        if (current != NO_INDEX && next < current) {
            return NO_INDEX;
        }
        return next;
    }

    /**
     * The state of the watch of a service. Query results are handled one at a time on the scheduler, while lookups
     * read the state from the caller threads: the instances and their states are published together as an immutable
     * {@link Snapshot}.
     */
    private final class ServiceWatch {
        final String serviceId;
        final CompletableFuture<List<ServiceInstance>> firstResult = new CompletableFuture<>();
        final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();
        final AtomicLong index = new AtomicLong(NO_INDEX);
        final AtomicInteger errors = new AtomicInteger();
        volatile boolean failing;
        volatile boolean disposed;
        volatile long lastAccess = System.nanoTime();
        // guarded by this
        private @Nullable Disposable subscription;
        private boolean started;

        ServiceWatch(String serviceId) {
            this.serviceId = serviceId;
        }

        @Nullable List<ServiceInstance> instances() {
            Snapshot current = snapshot.get();
            return current != null ? current.instances() : null;
        }

        synchronized void start() {
            if (started || disposed) {
                return;
            }
            started = true;
            LOG.debug("Watching the instances of service {}", serviceId);
            subscription = poll().repeat(() -> !disposed).subscribe();
        }

        private Mono<Void> poll() {
            return Mono.defer(() -> {
                if (isIdle(this)) {
                    LOG.debug("Service {} has not been looked up for {}, no longer watching it", serviceId,
                        watchConfiguration.getIdleTimeout().orElse(null));
                    watches.remove(serviceId, this);
                    stop(this);
                    return Mono.empty();
                }
                Duration delay = nextDelay();
                Mono<HttpResponse<List<ConsulHealthEntry>>> query = Mono.defer(() -> query(this));
                if (index.get() == NO_INDEX) {
                    // an index-less query returns at once, bound it like a regular lookup
                    query = query.timeout(initialQueryTimeout);
                }
                if (!delay.isZero()) {
                    query = Mono.delay(delay).then(query);
                }
                return query
                    .publishOn(scheduler)
                    .doOnNext(response -> onResponse(this, response))
                    .onErrorResume(e -> {
                        onError(this, e);
                        return Mono.empty();
                    })
                    .then();
            });
        }

        synchronized void dispose() {
            disposed = true;
            Disposable current = subscription;
            if (current != null) {
                current.dispose();
            }
        }

        Duration nextDelay() {
            if (failing) {
                long retry = watchConfiguration.getRetryDelay().toMillis();
                long max = watchConfiguration.getMaxRetryDelay().toMillis();
                int shift = Math.clamp(errors.get() - 1L, 0, 20);
                return Duration.ofMillis(Math.min(max, retry << shift));
            }
            if (snapshot.get() == null && index.get() == NO_INDEX) {
                return Duration.ZERO;
            }
            return watchConfiguration.getDelay();
        }
    }

    /**
     * The result of the last query of a service.
     *
     * @param instances The instances, immutable
     * @param states    The parts of the instances whose change triggers an event, immutable
     */
    private record Snapshot(List<ServiceInstance> instances, Set<InstanceState> states) {
    }

    /**
     * The parts of an instance whose change triggers a {@link ServiceInstancesChangedEvent}: the output of checks
     * is ignored.
     *
     * @param id      The service instance ID
     * @param node    The node name
     * @param address The address of the instance
     * @param port    The port of the instance
     * @param tags    The tags of the instance
     * @param meta    The metadata of the instance
     * @param checks  The status of each check
     */
    private record InstanceState(@Nullable String id,
                                 @Nullable String node,
                                 @Nullable String address,
                                 @Nullable Integer port,
                                 @Nullable List<String> tags,
                                 @Nullable Map<String, String> meta,
                                 Map<String, String> checks) {

        static InstanceState of(ConsulHealthEntry entry) {
            ConsulServiceEntry service = entry.service();
            Map<String, String> checks = new TreeMap<>();
            if (entry.checks() != null) {
                for (ConsulCheck check : entry.checks()) {
                    checks.put(check.getId() + '/' + check.getName(), check.getStatus());
                }
            }
            String address = service != null ? service.address() : null;
            if (address == null && entry.node() != null) {
                address = entry.node().address();
            }
            return new InstanceState(
                service != null ? service.id() : null,
                entry.node() != null ? entry.node().node() : null,
                address,
                service != null ? service.port() : null,
                service != null ? service.tags() : null,
                service != null ? service.meta() : null,
                checks
            );
        }
    }
}
