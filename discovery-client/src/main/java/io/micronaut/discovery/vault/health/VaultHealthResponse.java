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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

/**
 * The body of Vault's <a href="https://developer.hashicorp.com/vault/api-docs/system/health">{@code GET /v1/sys/health}</a> response.
 *
 * @param initialized                Whether the Vault server is initialized
 * @param sealed                     Whether the Vault server is sealed
 * @param standby                    Whether the Vault server is a standby node
 * @param performanceStandby         Whether the Vault server is a performance standby node
 * @param replicationPerformanceMode The performance replication mode
 * @param replicationDrMode          The disaster recovery replication mode
 * @param serverTimeUtc              The server time in seconds since the epoch
 * @param version                    The Vault version
 * @param clusterName                The cluster name
 * @param clusterId                  The cluster ID
 * @author graemerocher
 * @since 5.3.0
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record VaultHealthResponse(
    @Nullable @JsonProperty("initialized") Boolean initialized,
    @Nullable @JsonProperty("sealed") Boolean sealed,
    @Nullable @JsonProperty("standby") Boolean standby,
    @Nullable @JsonProperty("performance_standby") Boolean performanceStandby,
    @Nullable @JsonProperty("replication_performance_mode") String replicationPerformanceMode,
    @Nullable @JsonProperty("replication_dr_mode") String replicationDrMode,
    @Nullable @JsonProperty("server_time_utc") Long serverTimeUtc,
    @Nullable @JsonProperty("version") String version,
    @Nullable @JsonProperty("cluster_name") String clusterName,
    @Nullable @JsonProperty("cluster_id") String clusterId) {
}
