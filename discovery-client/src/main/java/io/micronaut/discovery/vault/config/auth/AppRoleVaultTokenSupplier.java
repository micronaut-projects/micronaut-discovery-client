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
import io.micronaut.discovery.vault.config.VaultClientConfiguration.VaultClientAppRoleConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logs in to Vault with the <a href="https://developer.hashicorp.com/vault/docs/auth/approle">AppRole</a> auth method.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public final class AppRoleVaultTokenSupplier extends AbstractVaultLoginTokenSupplier {

    private final VaultClientAppRoleConfiguration configuration;

    /**
     * @param authClient The client used to call the Vault login endpoint
     * @param configuration The AppRole configuration
     */
    public AppRoleVaultTokenSupplier(VaultAuthHttpClient authClient, VaultClientAppRoleConfiguration configuration) {
        super(authClient);
        this.configuration = configuration;
    }

    @Override
    protected String getMethodName() {
        return "AppRole";
    }

    @Override
    protected String getMountPath() {
        return configuration.getPath();
    }

    @Override
    protected Map<String, Object> getLoginBody() {
        String roleId = configuration.getRoleId();
        if (StringUtils.isEmpty(roleId)) {
            throw new ConfigurationException("Vault AppRole authentication requires [" + VaultClientAppRoleConfiguration.PREFIX + ".role-id]");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role_id", roleId);
        String secretId = configuration.getSecretId();
        if (StringUtils.isNotEmpty(secretId)) {
            body.put("secret_id", secretId);
        }
        return body;
    }
}
