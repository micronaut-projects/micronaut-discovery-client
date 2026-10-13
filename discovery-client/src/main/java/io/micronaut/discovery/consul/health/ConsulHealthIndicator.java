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
package io.micronaut.discovery.consul.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.ConsulCheck;
import io.micronaut.discovery.consul.client.v1.ConsulCheckStatus;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.ConsulHealthEntry;
import io.micronaut.discovery.consul.client.v1.ConsulServiceEntry;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link HealthIndicator} for Consul.
 *
 * <p>Reports the current Consul leader. When {@code consul.client.health-check-services} is enabled the
 * catalog services and the checks of the service registered for this application are reported as
 * details too. The service checks never change the reported status, since Consul HTTP checks may call
 * the health endpoint of this application.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(classes = HealthIndicator.class)
@Requires(beans = ConsulClient.class)
@Requires(property = ConsulConfiguration.PREFIX + ".health-check", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class ConsulHealthIndicator implements HealthIndicator {

    private static final String NOT_REGISTERED = "not-registered";

    private final ConsulClient client;
    private final boolean includeServices;
    private final @Nullable String serviceName;

    /**
     * @param client The Consul client
     */
    public ConsulHealthIndicator(ConsulClient client) {
        this.client = client;
        this.includeServices = false;
        this.serviceName = null;
    }

    /**
     * @param client                   The Consul client
     * @param configuration            The Consul configuration
     * @param applicationConfiguration The application configuration
     * @since 5.3.0
     */
    @Inject
    public ConsulHealthIndicator(ConsulClient client,
                                 ConsulConfiguration configuration,
                                 ApplicationConfiguration applicationConfiguration) {
        this.client = client;
        this.includeServices = configuration.isHealthCheckServices();
        this.serviceName = applicationConfiguration.getName().orElse(null);
    }

    @Override
    public Publisher<HealthResult> getResult() {
        Flux<String> statusFlowable = Flux.from(client.status());

        return statusFlowable.flatMap(leader -> {
            if (!includeServices) {
                return Mono.just(up(Collections.singletonMap("leader", leader)));
            }
            return Mono.zip(catalogServices(), registeredService()).map(tuple -> {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("leader", leader);
                details.putAll(tuple.getT1());
                details.putAll(tuple.getT2());
                return up(details);
            });
        }).onErrorResume(throwable -> {
            HealthResult.Builder builder = HealthResult.builder(ConsulClient.SERVICE_ID, HealthStatus.DOWN);
            builder.exception(throwable);
            return Flux.just(builder.build());
        });
    }

    private static HealthResult up(Map<String, Object> details) {
        HealthResult.Builder builder = HealthResult.builder(ConsulClient.SERVICE_ID, HealthStatus.UP);
        builder.details(details);
        return builder.build();
    }

    private Mono<Map<String, Object>> catalogServices() {
        return Mono.from(client.getServiceNames())
            .<Map<String, Object>>map(services -> Collections.singletonMap("services", services))
            .defaultIfEmpty(Collections.singletonMap("services", Collections.emptyMap()))
            .onErrorResume(throwable -> Mono.just(Collections.singletonMap("services-error", errorMessage(throwable))));
    }

    private Mono<Map<String, Object>> registeredService() {
        String name = serviceName;
        if (name == null) {
            return Mono.just(Collections.emptyMap());
        }
        return Mono.from(client.findHealthyServices(name))
            .defaultIfEmpty(Collections.emptyList())
            .<Map<String, Object>>map(entries -> Collections.singletonMap("service", serviceDetails(name, entries)))
            .onErrorResume(throwable -> Mono.just(Collections.singletonMap("service-error", errorMessage(throwable))));
    }

    private static Map<String, Object> serviceDetails(String name, List<ConsulHealthEntry> entries) {
        List<Map<String, Object>> instances = new ArrayList<>(entries.size());
        String serviceStatus = entries.isEmpty() ? NOT_REGISTERED : ConsulCheckStatus.PASSING.toString();
        for (ConsulHealthEntry entry : entries) {
            Map<String, Object> instance = new LinkedHashMap<>();
            ConsulServiceEntry service = entry.service();
            if (service != null) {
                putIfNotNull(instance, "id", service.id());
                putIfNotNull(instance, "address", service.address());
                putIfNotNull(instance, "port", service.port());
            }
            if (entry.node() != null) {
                putIfNotNull(instance, "node", entry.node().node());
            }
            String instanceStatus = ConsulCheckStatus.PASSING.toString();
            List<Map<String, Object>> checks = new ArrayList<>();
            if (entry.checks() != null) {
                for (ConsulCheck check : entry.checks()) {
                    Map<String, Object> checkDetails = new LinkedHashMap<>();
                    putIfNotNull(checkDetails, "id", check.getId());
                    putIfNotNull(checkDetails, "name", check.getName());
                    putIfNotNull(checkDetails, "status", check.getStatus());
                    checks.add(checkDetails);
                    instanceStatus = worst(instanceStatus, check.getStatus());
                }
            }
            instance.put("status", instanceStatus);
            instance.put("checks", checks);
            instances.add(instance);
            serviceStatus = worst(serviceStatus, instanceStatus);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", name);
        details.put("status", serviceStatus);
        details.put("instances", instances);
        return details;
    }

    private static String worst(String current, @Nullable String candidate) {
        if (candidate == null) {
            return current;
        }
        return severity(candidate) > severity(current) ? candidate : current;
    }

    private static int severity(String status) {
        if (ConsulCheckStatus.PASSING.toString().equalsIgnoreCase(status)) {
            return 0;
        }
        if (ConsulCheckStatus.WARNING.toString().equalsIgnoreCase(status)) {
            return 1;
        }
        // critical, maintenance or any status this client does not know about
        return 2;
    }

    private static String errorMessage(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getName();
    }

    private static void putIfNotNull(Map<String, Object> details, String key, @Nullable Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }
}
