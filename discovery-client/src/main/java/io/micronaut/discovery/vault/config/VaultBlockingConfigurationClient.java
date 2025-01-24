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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.client.config.BlockingConfigurationClient;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 *  A {@link ConfigurationClient} for Vault Configuration.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@Singleton
@BootstrapContextCompatible
@Requires(beans = VaultClientConfiguration.class)
public class VaultBlockingConfigurationClient implements BlockingConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(VaultBlockingConfigurationClient.class);
    private static final String DEFAULT_APPLICATION = "application";

    private final VaultConfigBlockingHttpClient<?> configHttpClient;
    private final VaultClientConfiguration vaultClientConfiguration;
    private final ApplicationConfiguration applicationConfiguration;

    /**
     * Default Constructor.
     *
     * @param configHttpClient          The http client
     * @param vaultClientConfiguration  Vault Client Configuration
     * @param applicationConfiguration  The application configuration
     */
    public VaultBlockingConfigurationClient(VaultConfigBlockingHttpClient<?> configHttpClient,
                                    VaultClientConfiguration vaultClientConfiguration,
                                    ApplicationConfiguration applicationConfiguration) {
        this.configHttpClient = configHttpClient;
        this.vaultClientConfiguration = vaultClientConfiguration;
        this.applicationConfiguration = applicationConfiguration;
    }

    @Override
    @NonNull
    public List<PropertySource> getPropertySources(@NonNull Environment environment) {
        if (!vaultClientConfiguration.getDiscoveryConfiguration().isEnabled()) {
            return Collections.emptyList();
        }

        final String applicationName = applicationConfiguration.getName().orElse(null);
        final Set<String> activeNames = environment.getActiveNames();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Vault server endpoint: {}, secret engine version: {}, secret-engine-name: {}, vault keys path prefix: {}",
                    vaultClientConfiguration.getUri(),
                    vaultClientConfiguration.getKvVersion(),
                    vaultClientConfiguration.getSecretEngineName(),
                    vaultClientConfiguration.getPathPrefix());
            LOG.debug("Application name: {}, application profiles: {}", applicationName, activeNames);
        }

        List<PropertySource> propertySources = new ArrayList<>();

        String token = vaultClientConfiguration.getToken();
        String engine = vaultClientConfiguration.getSecretEngineName();
        String pathPrefix = normalizePathPrefix(vaultClientConfiguration.getPathPrefix());
        buildVaultKeys(pathPrefix, applicationName, activeNames)
            .forEach((key, value) -> invoke(token, engine, value)
                .filter(data -> !data.getSecrets().isEmpty())
                .map(data -> PropertySource.of(value, data.getSecrets(), key))
                .ifPresent(propertySources::add));
        return  propertySources;
    }

    private Optional<AbstractVaultResponse<?>> invoke(String token,
                                                      String engine,
                                                      String value) {
        try {
            return Optional.of(configHttpClient.readConfigurationValues(token, engine, value));
        } catch (Throwable t) {
            if (t instanceof HttpClientResponseException hcre) {
                if (hcre.getStatus() == HttpStatus.NOT_FOUND) {
                    if (vaultClientConfiguration.isFailFast()) {
                        throw new ConfigurationException(
                            "Could not locate PropertySource and the fail fast property is set", t);
                    }
                }
                return Optional.empty();
            }
            throw new ConfigurationException("Error reading distributed configuration from Vault: " + t.getMessage(), t);
        }
    }

    /**
     * Builds the keys used to get vault properties.
     *
     * @param pathPrefix The prefix path of vault keys
     * @param applicationName The application name
     * @param environmentNames The active environments
     * @return map of vault keys
     */
    protected Map<Integer, String> buildVaultKeys(@Nullable String pathPrefix,
                                                  @Nullable String applicationName,
                                                  Set<String> environmentNames) {
        Map<Integer, String> vaultKeys = new HashMap<>();

        int baseOrder = EnvironmentPropertySource.POSITION + 100;
        int envOrder = baseOrder + 50;

        vaultKeys.put(++baseOrder, DEFAULT_APPLICATION);
        if (applicationName != null) {
            vaultKeys.put(++baseOrder, applicationName);
        }

        for (String activeName : environmentNames) {
            vaultKeys.put(++envOrder, DEFAULT_APPLICATION + "/" + activeName);
            if (applicationName != null) {
                vaultKeys.put(++envOrder, applicationName + "/" + activeName);
            }
        }

        return StringUtils.isNotEmpty(pathPrefix)
                   ? prefixVaultKeys(pathPrefix, vaultKeys)
                   : vaultKeys;
    }

    private Map<Integer, String> prefixVaultKeys(String prefix, Map<Integer, String> vaultKeys) {
        return vaultKeys.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> StringUtils.prependUri(prefix, entry.getValue())));
    }

    private String normalizePathPrefix(String prefix) {
        if (prefix.length() > 0 && prefix.charAt(0) == '/') {
            return prefix.substring(1);
        }
        return prefix;
    }

    @Override
    public @NonNull String getDescription() {
        return configHttpClient.getDescription();
    }
}
