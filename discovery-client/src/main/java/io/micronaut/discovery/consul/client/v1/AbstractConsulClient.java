/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.consul.client.v1;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.ConsulServiceInstance;
import io.micronaut.discovery.consul.discovery.ConsulServiceInstanceWatcher;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract implementation of {@link ConsulClient} that also implements {@link io.micronaut.discovery.DiscoveryClient}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = ConsulConfiguration.class)
@Requires(beans = ConsulConfiguration.class)
@TypeHint({ConsulCheck.class})
@BootstrapContextCompatible
public abstract class AbstractConsulClient implements ConsulClient {

    static final String CONSUL_REGISTRATION_RETRY_COUNT = "${" + ConsulConfiguration.ConsulRegistrationConfiguration.PREFIX + ".retry-count:3}";
    static final String CONSUL_REGISTRATION_RETRY_DELAY = "${" + ConsulConfiguration.ConsulRegistrationConfiguration.PREFIX + ".retry-delay:3s}";
    static final String EXPR_CONSUL_CONFIG_RETRY_COUNT = "${" + ConsulConfiguration.ConsulConfigDiscoveryConfiguration.PREFIX + ".retry-count:3}";
    static final String EXPR_CONSUL_CONFIG_RETRY_DELAY = "${" + ConsulConfiguration.ConsulConfigDiscoveryConfiguration.PREFIX + ".retry-delay:1s}";

    private ConsulConfiguration consulConfiguration = new ConsulConfiguration();
    private @Nullable ConsulServiceInstanceWatcher serviceInstanceWatcher;

    /**
     * @param consulConfiguration The Consul configuration
     */
    @Inject
    public void setConsulConfiguration(ConsulConfiguration consulConfiguration) {
        if (consulConfiguration != null) {
            this.consulConfiguration = consulConfiguration;
        }
    }

    /**
     * @param serviceInstanceWatcher The watcher serving instances when {@code consul.client.discovery.watch.enabled} is set
     */
    @Inject
    void setServiceInstanceWatcher(@Nullable ConsulServiceInstanceWatcher serviceInstanceWatcher) {
        this.serviceInstanceWatcher = serviceInstanceWatcher;
    }

    @Override
    public String getDescription() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        if (!consulConfiguration.getDiscovery().isEnabled()) {
            return Publishers.just(Collections.emptyList());
        } else {
            return ConsulClient.super.getServiceIds();
        }
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        if (!consulConfiguration.getDiscovery().isEnabled()) {
            return Publishers.just(Collections.emptyList());
        }
        serviceId = NameUtils.hyphenate(serviceId);
        if (SERVICE_ID.equals(serviceId)) {
            return Publishers.just(
                Collections.singletonList(ServiceInstance.of(SERVICE_ID, consulConfiguration.getHost(), consulConfiguration.getPort()))
            );
        }
        ConsulServiceInstanceWatcher watcher = serviceInstanceWatcher;
        if (watcher != null) {
            String id = serviceId;
            return watcher.getInstances(id, () -> queryInstances(id));
        }
        return queryInstances(serviceId);
    }

    private Publisher<List<ServiceInstance>> queryInstances(String serviceId) {
        ConsulConfiguration.ConsulDiscoveryConfiguration discovery = consulConfiguration.getDiscovery();
        boolean passing = discovery.isPassing();
        String datacenter = discovery.getDatacenters().get(serviceId);
        List<String> tags = discovery.resolveQueryTags(serviceId);
        String filter = discovery.resolveFilter(serviceId).orElse(null);
        Optional<String> scheme = Optional.ofNullable(discovery.getSchemes().get(serviceId));

        Publisher<List<ConsulHealthEntry>> healthyServicesPublisher = findHealthyServices(
            serviceId, passing, tags, datacenter, filter, discovery.getConsistencyMode());
        return Publishers.map(healthyServicesPublisher, healthEntries -> {
            List<ServiceInstance> serviceInstances = new ArrayList<>();
            for (ConsulHealthEntry healthEntry : healthEntries) {
                serviceInstances.add(new ConsulServiceInstance(healthEntry, scheme.orElse("http")));
            }
            return serviceInstances;
        });
    }

    @Override
    public Publisher<List<ConsulHealthEntry>> findHealthyServices(
        @NotNull String service,
        @Nullable Boolean passing,
        @Nullable List<String> tags,
        @Nullable String dc,
        @Nullable String filter,
        @Nullable ConsistencyMode consistencyMode) {
        return queryHealthService(
            service,
            passing,
            tags == null || tags.isEmpty() ? null : tags,
            dc,
            filter == null || filter.isEmpty() ? null : filter,
            ConsistencyMode.consistentFlag(consistencyMode),
            ConsistencyMode.staleFlag(consistencyMode)
        );
    }

    @Override
    public Publisher<List<KeyValue>> readValues(
        String key,
        @Nullable String datacenter,
        @Nullable Boolean raw,
        @Nullable String separator,
        @Nullable ConsistencyMode consistencyMode) {
        return queryKeyValues(
            key,
            datacenter,
            raw,
            separator,
            ConsistencyMode.consistentFlag(consistencyMode),
            ConsistencyMode.staleFlag(consistencyMode)
        );
    }

    /**
     * Queries the health of the instances of a service.
     *
     * @param service    The service
     * @param passing    Whether only passing instances are returned
     * @param tag        The tags, sent as repeated parameters
     * @param dc         The data center
     * @param filter     The filter expression
     * @param consistent The consistent flag
     * @param stale      The stale flag
     * @return The {@link ConsulHealthEntry} instances
     * @since 5.3.0
     */
    @Get(uri = "/health/service/{service}{?passing,tag*,dc,filter,consistent,stale}", single = true)
    protected abstract Publisher<List<ConsulHealthEntry>> queryHealthService(
        @NotNull String service,
        @Nullable Boolean passing,
        @Nullable List<String> tag,
        @Nullable String dc,
        @Nullable String filter,
        @Nullable Boolean consistent,
        @Nullable Boolean stale);

    /**
     * Reads keys recursively.
     *
     * @param key        The key
     * @param dc         The data center
     * @param raw        Whether the value should be raw
     * @param separator  The separator
     * @param consistent The consistent flag
     * @param stale      The stale flag
     * @return The {@link KeyValue} instances
     * @since 5.3.0
     */
    @Get(uri = "/kv/{+key}?recurse=true{&dc}{&raw}{&separator}{&consistent}{&stale}", single = true)
    @Retryable(
        attempts = AbstractConsulClient.EXPR_CONSUL_CONFIG_RETRY_COUNT,
        delay = AbstractConsulClient.EXPR_CONSUL_CONFIG_RETRY_DELAY
    )
    protected abstract Publisher<List<KeyValue>> queryKeyValues(
        String key,
        @Nullable String dc,
        @Nullable Boolean raw,
        @Nullable String separator,
        @Nullable Boolean consistent,
        @Nullable Boolean stale);
}
