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
package io.micronaut.discovery.imports;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.config.ConfigurationClient;

import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the importer-owned application context used to access existing distributed configuration clients.
 */
@Internal
public final class RemoteConfigImporterContextFactory {

    private static final Set<String> INCLUDED_PACKAGES = Set.of(
        "io.micronaut.context",
        "io.micronaut.core",
        "io.micronaut.discovery.consul",
        "io.micronaut.discovery.vault",
        "io.micronaut.discovery.spring",
        "io.micronaut.http",
        "io.micronaut.http.client",
        "io.micronaut.jackson",
        "io.micronaut.json",
        "io.micronaut.retry",
        "io.micronaut.runtime",
        "io.micronaut.scheduling",
        "io.micronaut.scheduling.executor"
    );

    private static final String CONSUL_REGISTRATION_ENABLED = "consul.client.registration.enabled";
    private static final String CONSUL_DISCOVERY_ENABLED = "consul.client.discovery.enabled";
    private static final String CONSUL_CONFIG_ENABLED = "consul.client.config.enabled";
    private static final String CONSUL_HOST = "consul.client.host";
    private static final String CONSUL_PORT = "consul.client.port";
    private static final String SPRING_CLOUD_CONFIG_ENABLED = "spring.cloud.config.enabled";
    private static final String SPRING_CLOUD_CONFIG_URI = "spring.cloud.config.uri";
    private static final String VAULT_CONFIG_ENABLED = "vault.client.config.enabled";
    private static final String VAULT_CLIENT_URI = "vault.client.uri";

    public ApplicationContext build(Map<String, Object> providerProperties) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(ConfigurationClient.ENABLED, false);
        properties.put(CONSUL_REGISTRATION_ENABLED, false);
        properties.put(CONSUL_DISCOVERY_ENABLED, false);
        properties.put(CONSUL_CONFIG_ENABLED, false);
        properties.put(SPRING_CLOUD_CONFIG_ENABLED, false);
        properties.put(VAULT_CONFIG_ENABLED, false);
        properties.putAll(providerProperties);

        if (properties.containsKey(CONSUL_HOST) || properties.containsKey(CONSUL_PORT)) {
            properties.put(CONSUL_CONFIG_ENABLED, true);
        }
        if (properties.containsKey(VAULT_CLIENT_URI)) {
            properties.put(VAULT_CONFIG_ENABLED, true);
        }
        if (properties.containsKey(SPRING_CLOUD_CONFIG_URI)) {
            properties.put(SPRING_CLOUD_CONFIG_ENABLED, true);
        }

        ApplicationContextBuilder builder = ApplicationContext.builder()
            .beanConfigurationsPredicate(beanConfiguration -> INCLUDED_PACKAGES.stream().anyMatch(beanConfiguration.getPackage().getName()::startsWith))
            .eventsEnabled(false)
            .eagerBeansEnabled(false)
            .deducePackage(false)
            .bootstrapEnvironment(false)
            .deduceCloudEnvironment(false)
            .enableDefaultPropertySources(false)
            .propertySources(PropertySource.of("config", properties, PropertySource.PropertyConvention.JAVA_PROPERTIES, PropertySource.Origin.of("config")));

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            builder.classLoader(contextClassLoader);
        }
        return builder.start();
    }
}
