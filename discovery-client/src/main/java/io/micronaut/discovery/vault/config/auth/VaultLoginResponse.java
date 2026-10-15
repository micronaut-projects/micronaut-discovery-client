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
package io.micronaut.discovery.vault.config.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The response of a Vault {@code auth/{path}/login} request.
 *
 * @param auth The authentication data, including the client token
 * @author graemerocher
 * @since 5.3.0
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record VaultLoginResponse(@Nullable Auth auth) {

    /**
     * The {@code auth} section of a login response.
     *
     * @param clientToken The Vault token to use for subsequent requests
     * @param leaseDuration The token lease duration in seconds
     * @param renewable Whether the token can be renewed
     * @param policies The policies attached to the token
     */
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Auth(
        @JsonProperty("client_token") @Nullable String clientToken,
        @JsonProperty("lease_duration") long leaseDuration,
        @JsonProperty("renewable") boolean renewable,
        @JsonProperty("policies") @Nullable List<String> policies) {
    }
}
