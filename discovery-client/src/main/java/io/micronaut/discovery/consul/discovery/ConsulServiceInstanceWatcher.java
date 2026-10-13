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
import java.util.function.Supplier;

/**
 * Keeps the instances of each looked up Consul service up to date with a blocking query on
 * {@code /v1/health/service/{service}} and serves {@link io.micronaut.discovery.DiscoveryClient#getInstances(String)}
 * from that cache. A service is watched from its first lookup until the application context is closed.
 *
 * <p>While the watch of a service has no result yet the lookup waits for the first query; while the watch is failing
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

    private final ConsulServiceWatchClient client;
    private final ConsulConfiguration consulConfiguration;
    private final ConsulServiceWatchConfiguration watchConfiguration;
    private final ApplicationEventPublisher<ServiceInstancesChangedEvent> eventPublisher;
    private final Scheduler scheduler;
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
        watch.start();
        if (closed) {
            stop(watch);
            return directQuery.get();
        }
        if (!watch.failing) {
            List<ServiceInstance> instances = watch.instances;
            if (instances != null) {
                return Mono.just(instances);
            }
            return Mono.fromFuture(watch.firstResult, true)
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
        return watch != null && watch.instances != null && !watch.failing;
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

    private Mono<Void> poll(ServiceWatch watch) {
        return Mono.defer(() -> {
            Duration delay = watch.nextDelay();
            Mono<HttpResponse<List<ConsulHealthEntry>>> query = Mono.defer(() -> query(watch));
            if (!delay.isZero()) {
                query = Mono.delay(delay).then(query);
            }
            return query
                .publishOn(scheduler)
                .doOnNext(response -> onResponse(watch, response))
                .onErrorResume(e -> {
                    onError(watch, e);
                    return Mono.empty();
                })
                .then();
        });
    }

    private Mono<HttpResponse<List<ConsulHealthEntry>>> query(ServiceWatch watch) {
        String serviceId = watch.serviceId;
        ConsulConfiguration.ConsulDiscoveryConfiguration discovery = consulConfiguration.getDiscovery();
        List<String> tags = discovery.resolveQueryTags(serviceId);
        String filter = discovery.resolveFilter(serviceId).orElse(null);
        ConsistencyMode mode = discovery.getConsistencyMode();
        Long index = watch.index;
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
        watch.index = nextIndex(watch.index, response.getHeaders().get(ConsulServiceWatchClient.HEADER_CONSUL_INDEX));
        List<ConsulHealthEntry> entries = response.getBody().orElse(Collections.emptyList());
        String scheme = consulConfiguration.getDiscovery().getSchemes().getOrDefault(watch.serviceId, "http");
        List<ServiceInstance> instances = new ArrayList<>(entries.size());
        Set<InstanceState> states = new HashSet<>();
        for (ConsulHealthEntry entry : entries) {
            instances.add(new ConsulServiceInstance(entry, scheme));
            states.add(InstanceState.of(entry));
        }
        instances = Collections.unmodifiableList(instances);

        List<ServiceInstance> previous = watch.instances;
        boolean changed = previous != null && !states.equals(watch.states);
        watch.states = states;
        watch.instances = instances;
        if (watch.failing) {
            LOG.info("The watch of service {} recovered", watch.serviceId);
        }
        watch.failing = false;
        watch.errors = 0;
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
        if (e instanceof ReadTimeoutException && watch.instances != null) {
            LOG.debug("Blocking query of service {} timed out", watch.serviceId);
            return;
        }
        watch.errors++;
        if (!watch.failing) {
            LOG.warn("Watching service {} failed, querying Consul directly until the watch recovers: {}", watch.serviceId, e.getMessage());
            LOG.debug("Watch failure of service {}", watch.serviceId, e);
        }
        watch.failing = true;
        watch.firstResult.completeExceptionally(e);
    }

    /**
     * Applies the index rules of https://developer.hashicorp.com/consul/api-docs/features/blocking#implementation-details.
     */
    private static @Nullable Long nextIndex(@Nullable Long current, @Nullable String header) {
        if (header == null) {
            return null;
        }
        long next;
        try {
            next = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (next <= 0) {
            return 1L;
        }
        if (current != null && next < current) {
            return null;
        }
        return next;
    }

    /**
     * The state of the watch of a service.
     */
    private final class ServiceWatch {
        final String serviceId;
        final CompletableFuture<List<ServiceInstance>> firstResult = new CompletableFuture<>();
        volatile @Nullable Disposable subscription;
        volatile @Nullable List<ServiceInstance> instances;
        volatile Set<InstanceState> states = Collections.emptySet();
        volatile @Nullable Long index;
        volatile boolean failing;
        volatile boolean disposed;
        volatile int errors;
        private boolean started;

        ServiceWatch(String serviceId) {
            this.serviceId = serviceId;
        }

        synchronized void start() {
            if (started || disposed) {
                return;
            }
            started = true;
            LOG.debug("Watching the instances of service {}", serviceId);
            subscription = poll(this).repeat(() -> !disposed).subscribe();
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
                int shift = Math.min(Math.max(errors - 1, 0), 20);
                return Duration.ofMillis(Math.min(max, retry << shift));
            }
            if (instances == null && index == null) {
                return Duration.ZERO;
            }
            return watchConfiguration.getDelay();
        }
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
            return new InstanceState(
                service != null ? service.id() : null,
                entry.node() != null ? entry.node().node() : null,
                service != null && service.address() != null ? service.address() : entry.node() != null ? entry.node().address() : null,
                service != null ? service.port() : null,
                service != null ? service.tags() : null,
                service != null ? service.meta() : null,
                checks
            );
        }
    }
}
