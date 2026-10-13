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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.vault.config.VaultClientConfiguration;
import io.micronaut.discovery.vault.config.VaultClientConfiguration.VaultAuthentication;
import io.micronaut.discovery.vault.config.VaultClientConfiguration.VaultClientAppRoleConfiguration;
import io.micronaut.discovery.vault.config.VaultClientConfiguration.VaultClientKubernetesConfiguration;
import jakarta.inject.Singleton;

/**
 * Creates the built-in {@link VaultTokenSupplier} selected by {@code vault.client.authentication}. The bean is
 * {@link Secondary}, so a user-defined {@link VaultTokenSupplier} bean takes precedence.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Factory
@BootstrapContextCompatible
@Requires(beans = VaultClientConfiguration.class)
@Internal
final class VaultTokenSupplierFactory {

    @Singleton
    @Secondary
    @BootstrapContextCompatible
    VaultTokenSupplier vaultTokenSupplier(VaultClientConfiguration configuration,
                                          BeanProvider<VaultAuthHttpClient> authClient,
                                          BeanProvider<VaultClientAppRoleConfiguration> appRole,
                                          BeanProvider<VaultClientKubernetesConfiguration> kubernetes) {
        VaultAuthentication authentication = configuration.getAuthentication();
        return switch (authentication) {
            case APPROLE -> new AppRoleVaultTokenSupplier(authClient.get(), appRole.get());
            case KUBERNETES -> new KubernetesVaultTokenSupplier(authClient.get(), kubernetes.get());
            case TOKEN -> new StaticVaultTokenSupplier(configuration);
        };
    }
}
