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
package io.micronaut.discovery.vault.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.vault.config.VaultClientConfiguration;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link HealthIndicator} for HashiCorp Vault based on the
 * <a href="https://developer.hashicorp.com/vault/api-docs/system/health">{@code GET /v1/sys/health}</a> endpoint.
 *
 * <p>The documented status codes are mapped as follows:</p>
 * <ul>
 *     <li>{@code 200} (initialized, unsealed and active): {@link HealthStatus#UP}</li>
 *     <li>{@code 429} (unsealed standby): {@link HealthStatus#UP}</li>
 *     <li>{@code 472} (disaster recovery secondary): {@link HealthStatus#UP}</li>
 *     <li>{@code 473} (performance standby): {@link HealthStatus#UP}</li>
 *     <li>{@code 501} (not initialized), {@code 503} (sealed), {@code 530} (removed from the cluster)
 *     and any other status: {@link HealthStatus#DOWN}</li>
 * </ul>
 *
 * <p>The node state, version, seal and standby flags are reported as details.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Requires(classes = HealthIndicator.class)
@Requires(beans = VaultClientConfiguration.class)
@Requires(property = VaultClientConfiguration.PREFIX + ".health-check", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class VaultHealthIndicator implements HealthIndicator {

    /**
     * The name of the health result.
     */
    public static final String NAME = "vault";

    private static final int ACTIVE = 200;
    private static final int STANDBY = 429;
    private static final int DR_SECONDARY = 472;
    private static final int PERFORMANCE_STANDBY = 473;
    private static final int NOT_INITIALIZED = 501;
    private static final int SEALED = 503;
    private static final int REMOVED_FROM_CLUSTER = 530;

    private final VaultHealthClient client;

    /**
     * @param client The Vault health client
     */
    public VaultHealthIndicator(VaultHealthClient client) {
        this.client = client;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return Mono.from(client.health())
            .map(response -> toResult(response.code(), response.getBody().orElse(null)))
            // the Micronaut Reactor HTTP client turns a 404 response into an empty result
            .switchIfEmpty(Mono.fromSupplier(VaultHealthIndicator::noResponse))
            .onErrorResume(HttpClientResponseException.class, e -> {
                HttpResponse<?> response = e.getResponse();
                return Mono.just(toResult(response.code(), response.getBody(VaultHealthResponse.class).orElse(null)));
            })
            .onErrorResume(throwable -> Mono.just(HealthResult.builder(NAME, HealthStatus.DOWN)
                .exception(throwable)
                .build()));
    }

    private static HealthResult noResponse() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", "unknown");
        details.put("error", "Vault returned no health response");
        return HealthResult.builder(NAME, HealthStatus.DOWN).details(details).build();
    }

    private static HealthResult toResult(int code, @Nullable VaultHealthResponse body) {
        HealthStatus status = switch (code) {
            case ACTIVE, STANDBY, DR_SECONDARY, PERFORMANCE_STANDBY -> HealthStatus.UP;
            default -> HealthStatus.DOWN;
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", state(code));
        if (body != null) {
            putIfNotNull(details, "version", body.version());
            putIfNotNull(details, "initialized", body.initialized());
            putIfNotNull(details, "sealed", body.sealed());
            putIfNotNull(details, "standby", body.standby());
            putIfNotNull(details, "performance-standby", body.performanceStandby());
            putIfNotNull(details, "replication-performance-mode", body.replicationPerformanceMode());
            putIfNotNull(details, "replication-dr-mode", body.replicationDrMode());
            putIfNotNull(details, "cluster-name", body.clusterName());
            putIfNotNull(details, "cluster-id", body.clusterId());
            putIfNotNull(details, "server-time-utc", body.serverTimeUtc());
        } else {
            details.put("status-code", code);
        }
        return HealthResult.builder(NAME, status).details(details).build();
    }

    private static String state(int code) {
        return switch (code) {
            case ACTIVE -> "active";
            case STANDBY -> "standby";
            case DR_SECONDARY -> "dr-secondary";
            case PERFORMANCE_STANDBY -> "performance-standby";
            case NOT_INITIALIZED -> "uninitialized";
            case SEALED -> "sealed";
            case REMOVED_FROM_CLUSTER -> "removed-from-cluster";
            default -> "unknown";
        };
    }

    private static void putIfNotNull(Map<String, Object> details, String key, @Nullable Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }
}
