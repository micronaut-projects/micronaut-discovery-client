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
package io.micronaut.discovery.vault.imports;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.discovery.vault.config.AbstractVaultResponse;
import io.micronaut.discovery.vault.config.VaultClientConfiguration;
import io.micronaut.discovery.vault.config.VaultConfigHttpClient;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import reactor.core.publisher.Flux;

import java.util.Map;

final class VaultImportSupport {

    Map<String, Object> load(ApplicationContext context, String secretPath, boolean optional) {
        VaultClientConfiguration configuration = context.getBean(VaultClientConfiguration.class);
        @SuppressWarnings("unchecked")
        VaultConfigHttpClient<AbstractVaultResponse<?>> client = (VaultConfigHttpClient<AbstractVaultResponse<?>>) context.getBean(VaultConfigHttpClient.class);
        String vaultKey = buildVaultKey(configuration, secretPath);

        try {
            AbstractVaultResponse<?> response = Flux.from(client.readConfigurationValues(
                configuration.getToken(),
                configuration.getSecretEngineName(),
                vaultKey
            )).blockFirst();
            return response != null ? response.getSecrets() : Map.of();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (optional && e instanceof HttpClientResponseException hcre && hcre.getStatus() == HttpStatus.NOT_FOUND) {
                return Map.of();
            }
            if (cause instanceof HttpClientResponseException hcre && hcre.getStatus() == HttpStatus.NOT_FOUND && optional) {
                return Map.of();
            }
            if (optional && e instanceof HttpClientResponseException hcre && hcre.getMessage() != null && hcre.getMessage().contains("Cannot deserialize value of type") && hcre.getMessage().contains("from Array value")) {
                return Map.of();
            }
            if (optional && cause instanceof HttpClientResponseException hcre && hcre.getMessage() != null && hcre.getMessage().contains("Cannot deserialize value of type") && hcre.getMessage().contains("from Array value")) {
                return Map.of();
            }
            throw new ConfigurationException("Error reading distributed configuration from Vault: " + cause.getMessage(), cause);
        }
    }

    private String buildVaultKey(VaultClientConfiguration configuration, String secretPath) {
        String normalizedPath = secretPath.startsWith("/") ? secretPath.substring(1) : secretPath;
        String prefix = configuration.getPathPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return normalizedPath;
        }
        return prefix + '/' + normalizedPath;
    }
}
