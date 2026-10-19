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
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.discovery.vault.config.VaultClientConfiguration;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import org.reactivestreams.Publisher;

/**
 * A non-blocking HTTP client for Vault's unauthenticated
 * <a href="https://developer.hashicorp.com/vault/api-docs/system/health">{@code GET /v1/sys/health}</a> endpoint.
 *
 * <p>Vault answers with a non-2xx status code for standby, sealed and uninitialized nodes, which the
 * client surfaces as an {@link io.micronaut.http.client.exceptions.HttpClientResponseException} whose
 * response still carries the {@link VaultHealthResponse} body.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Client(value = VaultClientConfiguration.VAULT_CLIENT_CONFIG_ENDPOINT, configuration = VaultClientConfiguration.class)
@Requires(beans = VaultClientConfiguration.class)
public interface VaultHealthClient {

    /**
     * Reads the health status of the Vault server.
     *
     * @return A {@link Publisher} that emits the response of the health endpoint
     */
    @Get("/v1/sys/health")
    @SingleResult
    Publisher<HttpResponse<VaultHealthResponse>> health();
}
