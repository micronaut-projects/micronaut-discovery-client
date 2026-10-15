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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.vault.config.VaultClientConfiguration.VaultClientKubernetesConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Logs in to Vault with the <a href="https://developer.hashicorp.com/vault/docs/auth/kubernetes">Kubernetes</a>
 * auth method, using the service account token mounted into the pod.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public final class KubernetesVaultTokenSupplier extends AbstractVaultLoginTokenSupplier {

    private final VaultClientKubernetesConfiguration configuration;

    /**
     * @param authClient The client used to call the Vault login endpoint
     * @param configuration The Kubernetes auth configuration
     */
    public KubernetesVaultTokenSupplier(VaultAuthHttpClient authClient, VaultClientKubernetesConfiguration configuration) {
        super(authClient);
        this.configuration = configuration;
    }

    @Override
    protected String getMethodName() {
        return "Kubernetes";
    }

    @Override
    protected String getMountPath() {
        return configuration.getPath();
    }

    @Override
    protected Map<String, Object> getLoginBody() {
        String role = configuration.getRole();
        if (StringUtils.isEmpty(role)) {
            throw new ConfigurationException("Vault Kubernetes authentication requires [" + VaultClientKubernetesConfiguration.PREFIX + ".role]");
        }
        return Map.of("role", role, "jwt", readServiceAccountToken());
    }

    private String readServiceAccountToken() {
        Path tokenFile = Path.of(configuration.getServiceAccountTokenFile());
        try {
            String jwt = Files.readString(tokenFile, StandardCharsets.UTF_8).strip();
            if (jwt.isEmpty()) {
                throw new ConfigurationException("Kubernetes service account token file [" + tokenFile + "] is empty");
            }
            return jwt;
        } catch (IOException e) {
            throw new ConfigurationException("Unable to read Kubernetes service account token file [" + tokenFile + "]: " + e.getMessage(), e);
        }
    }
}
