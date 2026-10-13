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

import org.reactivestreams.Publisher;

/**
 * Supplies the token sent as the {@code X-Vault-Token} header when reading configuration from HashiCorp Vault.
 *
 * <p>The built-in implementation is selected with {@code vault.client.authentication}. Register a bean of this
 * type to obtain the token in another way, for example by logging in with an authentication method that is not
 * supported out of the box. A user-defined bean takes precedence over the built-in one. Mark it with
 * {@link io.micronaut.context.annotation.BootstrapContextCompatible} when the legacy bootstrap configuration
 * client is used.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@FunctionalInterface
public interface VaultTokenSupplier {

    /**
     * Obtain the Vault token.
     *
     * @return A publisher that emits a single token, or completes empty when no token should be sent
     */
    Publisher<String> getToken();
}
