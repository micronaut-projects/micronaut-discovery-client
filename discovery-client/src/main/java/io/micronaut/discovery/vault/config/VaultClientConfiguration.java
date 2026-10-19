/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.vault.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import jakarta.inject.Inject;

/**
 *  A {@link HttpClientConfiguration} for Vault Client.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@ConfigurationProperties(VaultClientConfiguration.PREFIX)
@BootstrapContextCompatible
@Requires(property = VaultClientConfiguration.PREFIX)
public class VaultClientConfiguration extends HttpClientConfiguration {

    public static final String PREFIX = "vault.client";

    /**
     * Vault Server Endpoint.
     */
    public static final String VAULT_CLIENT_CONFIG_ENDPOINT = "${" + VaultClientConfiguration.PREFIX + ".uri}";

    /**
     * Default value for Fail fast.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_FAIL_FAST = false;

    /**
     * Default value for whether Vault is considered for health checks.
     * @since 5.3.0
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_HEALTH_CHECK = true;

    private static final String DEFAULT_URI = "http://localhost:8200";
    private static final String DEFAULT_SECRET_ENGINE = "secret";
    private static final String DEFAULT_PATH_PREFIX = "";
    private static final VaultKvVersion DEFAULT_KV_VERSION = VaultKvVersion.V2;

    /**
     * Vault Secret Engine versions.
     */
    public enum VaultKvVersion { V1, V2 }

    /**
     * Vault authentication methods.
     *
     * @since 5.3.0
     */
    public enum VaultAuthentication {
        /**
         * Use the static token configured with {@code vault.client.token}.
         */
        TOKEN,
        /**
         * Log in with the AppRole auth method configured under {@code vault.client.app-role}.
         */
        APPROLE,
        /**
         * Log in with the Kubernetes auth method configured under {@code vault.client.kubernetes}.
         */
        KUBERNETES
    }

    private final VaultClientConnectionPoolConfiguration vaultClientConnectionPoolConfiguration;
    private final VaultClientDiscoveryConfiguration vaultClientDiscoveryConfiguration = new VaultClientDiscoveryConfiguration();

    private String uri = DEFAULT_URI;
    private String token;
    private VaultKvVersion kvVersion = DEFAULT_KV_VERSION;
    private String secretEngineName = DEFAULT_SECRET_ENGINE;
    private String pathPrefix = DEFAULT_PATH_PREFIX;
    private boolean failFast = DEFAULT_FAIL_FAST;
    private VaultAuthentication authentication = VaultAuthentication.TOKEN;
    private String namespace;
    private boolean healthCheck = DEFAULT_HEALTH_CHECK;

    /**
     * @param vaultClientConnectionPoolConfiguration Vault Client Connection Pool Configuration
     * @param applicationConfiguration Application Configuration
     */
    @Inject
    public VaultClientConfiguration(VaultClientConnectionPoolConfiguration vaultClientConnectionPoolConfiguration, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.vaultClientConnectionPoolConfiguration = vaultClientConnectionPoolConfiguration;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return vaultClientConnectionPoolConfiguration;
    }

    /**
     * @return The discovery service configuration
     */
    public VaultClientDiscoveryConfiguration getDiscoveryConfiguration() {
        return vaultClientDiscoveryConfiguration;
    }

    /**
     * @return The Vault Server Uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * Set the Vault Server Uri. Default value ({@value #DEFAULT_URI}).
     *
     * @param uri Vault Server Uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * @return The Vault authentication token
     */
    public String getToken() {
        return token;
    }

    /**
     * Set the Vault authentication token.
     *
     * @param token Vault authentication token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * @return The Vault Secret engine version
     */
    public VaultKvVersion getKvVersion() {
        return kvVersion;
    }

    /**
     * Set the version of the Vault Secret engine. V1 or V2. Default value (V2).
     *
     * @param kvVersion The version of the Vault Secret engine
     */
    public void setKvVersion(VaultKvVersion kvVersion) {
        this.kvVersion = kvVersion;
    }

    /**
     * @return The Vault Secret engine name
     */
    public String getSecretEngineName() {
        return secretEngineName;
    }

    /**
     * Set the name of the Vault Secret engine name. Default value ({@value #DEFAULT_SECRET_ENGINE}).
     *
     * @param secretEngineName Vault Secret engine name
     */
    public void setSecretEngineName(String secretEngineName) {
        this.secretEngineName = secretEngineName;
    }

    /**
     * @return The prefix for the keys used to get vault properties
     */
    public String getPathPrefix() {
        return pathPrefix;
    }

    /**
     * Set the prefix for the keys used to get vault properties. Default value is empty string
     *
     * @param pathPrefix path which prefixes keys.
     */
    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    /**
     * @return Flag to indicate that failure to connect to HashiCorp Vault is fatal (default false).
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * If set to true an exception will be thrown if configuration is not found
     * for the application or any of its environments. Default value ({@value #DEFAULT_FAIL_FAST}).
     *
     * @param failFast Flag to fail fast
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * @return The authentication method used to obtain the Vault token
     * @since 5.3.0
     */
    public VaultAuthentication getAuthentication() {
        return authentication;
    }

    /**
     * Set the authentication method used to obtain the Vault token. Default value (TOKEN).
     * A user-defined {@link io.micronaut.discovery.vault.config.auth.VaultTokenSupplier} bean takes precedence.
     *
     * @param authentication The authentication method
     * @since 5.3.0
     */
    public void setAuthentication(VaultAuthentication authentication) {
        this.authentication = authentication != null ? authentication : VaultAuthentication.TOKEN;
    }

    /**
     * @return The Vault Enterprise namespace, or {@code null} if none is configured
     * @since 5.3.0
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Set the Vault Enterprise namespace. When set it is sent as the {@code X-Vault-Namespace} header on all
     * requests, including logins.
     *
     * @param namespace The namespace
     * @since 5.3.0
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * @return Whether the Vault server should be considered for health checks.
     * @see io.micronaut.discovery.vault.health.VaultHealthIndicator
     * @since 5.3.0
     */
    public boolean isHealthCheck() {
        return healthCheck;
    }

    /**
     * Sets whether the Vault server should be considered for health checks. When enabled and
     * {@code micronaut-management} is on the classpath, the health endpoint reports the result of
     * Vault's {@code /v1/sys/health} endpoint. Default value ({@value #DEFAULT_HEALTH_CHECK}).
     *
     * @param healthCheck True if it should
     * @see io.micronaut.discovery.vault.health.VaultHealthIndicator
     * @since 5.3.0
     */
    public void setHealthCheck(boolean healthCheck) {
        this.healthCheck = healthCheck;
    }

    /**
     * The Http Pool Connection Configuration class for Vault.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    @BootstrapContextCompatible
    @Requires(property = VaultClientConfiguration.PREFIX)
    public static class VaultClientConnectionPoolConfiguration extends ConnectionPoolConfiguration { }

    /**
     * The Discovery Configuration class for Vault.
     */
    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    @Requires(property = VaultClientConfiguration.PREFIX)
    public static class VaultClientDiscoveryConfiguration extends ConfigDiscoveryConfiguration {

        public static final String PREFIX = VaultClientConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;
    }

    /**
     * Configuration for the Vault AppRole auth method, used when {@code vault.client.authentication} is APPROLE.
     *
     * @since 5.3.0
     */
    @ConfigurationProperties(VaultClientAppRoleConfiguration.NAME)
    @BootstrapContextCompatible
    @Requires(property = VaultClientConfiguration.PREFIX)
    public static class VaultClientAppRoleConfiguration {

        public static final String NAME = "app-role";
        public static final String PREFIX = VaultClientConfiguration.PREFIX + "." + NAME;

        /**
         * Default AppRole mount path.
         */
        public static final String DEFAULT_PATH = "approle";

        private String roleId;
        private String secretId;
        private String path = DEFAULT_PATH;

        /**
         * @return The AppRole role ID
         */
        public String getRoleId() {
            return roleId;
        }

        /**
         * Set the AppRole role ID.
         *
         * @param roleId The role ID
         */
        public void setRoleId(String roleId) {
            this.roleId = roleId;
        }

        /**
         * @return The AppRole secret ID
         */
        public String getSecretId() {
            return secretId;
        }

        /**
         * Set the AppRole secret ID. Optional when the role does not require a secret ID.
         *
         * @param secretId The secret ID
         */
        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        /**
         * @return The path the AppRole auth method is mounted at
         */
        public String getPath() {
            return path;
        }

        /**
         * Set the path the AppRole auth method is mounted at. Default value ({@value #DEFAULT_PATH}).
         *
         * @param path The mount path
         */
        public void setPath(String path) {
            this.path = path;
        }
    }

    /**
     * Configuration for the Vault Kubernetes auth method, used when {@code vault.client.authentication} is KUBERNETES.
     *
     * @since 5.3.0
     */
    @ConfigurationProperties(VaultClientKubernetesConfiguration.NAME)
    @BootstrapContextCompatible
    @Requires(property = VaultClientConfiguration.PREFIX)
    public static class VaultClientKubernetesConfiguration {

        public static final String NAME = "kubernetes";
        public static final String PREFIX = VaultClientConfiguration.PREFIX + "." + NAME;

        /**
         * Default Kubernetes auth mount path.
         */
        public static final String DEFAULT_PATH = "kubernetes";

        /**
         * Default location of the Kubernetes service account token.
         */
        public static final String DEFAULT_SERVICE_ACCOUNT_TOKEN_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/token";

        private String role;
        private String path = DEFAULT_PATH;
        private String serviceAccountTokenFile = DEFAULT_SERVICE_ACCOUNT_TOKEN_FILE;

        /**
         * @return The Vault role to log in with
         */
        public String getRole() {
            return role;
        }

        /**
         * Set the Vault role to log in with.
         *
         * @param role The role
         */
        public void setRole(String role) {
            this.role = role;
        }

        /**
         * @return The path the Kubernetes auth method is mounted at
         */
        public String getPath() {
            return path;
        }

        /**
         * Set the path the Kubernetes auth method is mounted at. Default value ({@value #DEFAULT_PATH}).
         *
         * @param path The mount path
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * @return The file containing the service account token sent as the login JWT
         */
        public String getServiceAccountTokenFile() {
            return serviceAccountTokenFile;
        }

        /**
         * Set the file containing the service account token sent as the login JWT.
         * Default value ({@value #DEFAULT_SERVICE_ACCOUNT_TOKEN_FILE}).
         *
         * @param serviceAccountTokenFile The token file
         */
        public void setServiceAccountTokenFile(String serviceAccountTokenFile) {
            this.serviceAccountTokenFile = serviceAccountTokenFile;
        }
    }
}
