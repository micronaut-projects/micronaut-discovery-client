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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.discovery.vault.config.VaultClientConfiguration;
import io.micronaut.discovery.vault.config.VaultClientFilterMatcher;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * HTTP client for the Vault login endpoints. Can be used by custom {@link VaultTokenSupplier} implementations to
 * log in with auth methods that are not supported out of the box.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Client(value = VaultClientConfiguration.VAULT_CLIENT_CONFIG_ENDPOINT, configuration = VaultClientConfiguration.class)
@BootstrapContextCompatible
@Requires(beans = VaultClientConfiguration.class)
@VaultClientFilterMatcher
public interface VaultAuthHttpClient {

    /**
     * Log in to the auth method mounted at {@code auth/{mountPath}}.
     *
     * @param mountPath The auth method mount path, for example {@code approle} or {@code kubernetes}
     * @param body The login request body, specific to the auth method
     * @return A publisher that emits the login response
     */
    @Post("/v1/auth/{+mountPath}/login")
    @SingleResult
    Publisher<VaultLoginResponse> login(@PathVariable("mountPath") String mountPath, @Body Map<String, Object> body);
}
