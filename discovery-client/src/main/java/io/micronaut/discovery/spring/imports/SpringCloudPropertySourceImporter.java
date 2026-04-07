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
package io.micronaut.discovery.spring.imports;

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.discovery.config.RetryablePropertySourceImporter;
import io.micronaut.discovery.imports.RemoteConfigImporterContextFactory;
import io.micronaut.discovery.imports.RemoteConfigImportOptionBinder;
import io.micronaut.discovery.spring.config.SpringCloudClientConfiguration;
import io.micronaut.retry.RetryPolicy;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.EnvironmentPropertySource;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Property source importer for explicit Spring Cloud Config Server paths.
 */
@Internal
public final class SpringCloudPropertySourceImporter extends RetryablePropertySourceImporter<SpringCloudPropertySourceImporter.SpringCloudImport> {

    private static final String PROVIDER = "springcloud";
    private static final String LABEL = "spring.cloud.config.label";

    private final RemoteConfigImportOptionBinder optionBinder = new RemoteConfigImportOptionBinder();
    private final RemoteConfigImporterContextFactory contextFactory = new RemoteConfigImporterContextFactory();
    private final SpringCloudImportSupport importSupport = new SpringCloudImportSupport();
    private @Nullable ApplicationContext applicationContext;
    private @Nullable Map<String, Object> cachedContextProperties;

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    protected SpringCloudImport newImportDeclaration(ConnectionString connectionString, RetryPolicy retryPolicy) {
        Map<String, Object> properties = buildContextProperties(connectionString);
        String[] segments = connectionString.getPath().split("/");
        if (segments.length < 2) {
            return new SpringCloudImport(connectionString, properties, null, null, (String) properties.get(LABEL), connectionString.isOptional(), retryPolicy);
        }
        String applicationName = segments[0];
        String profiles = segments[1];
        String label = (String) properties.get(LABEL);
        return new SpringCloudImport(connectionString, properties, applicationName, profiles, label, connectionString.isOptional(), retryPolicy);
    }

    @Override
    protected SpringCloudImport newImportDeclaration(ConvertibleValues<Object> values, RetryPolicy retryPolicy) {
        String uri = values.get("uri", String.class)
            .or(() -> values.get("url", String.class))
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new ConfigurationException("Config import provider [springcloud] requires non-blank ['uri']"));
        String applicationName = values.get("application", String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new ConfigurationException("Config import provider [springcloud] requires non-blank ['application']"));
        String profiles = values.get("profiles", String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new ConfigurationException("Config import provider [springcloud] requires non-blank ['profiles']"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(SpringCloudClientConfiguration.PREFIX + ".uri", uri);
        values.get("label", String.class).ifPresent(v -> properties.put(LABEL, v));
        values.get("username", String.class).ifPresent(v -> properties.put("spring.cloud.config.username", v));
        values.get("password", String.class).ifPresent(v -> properties.put("spring.cloud.config.password", v));
        values.get("fail-fast", Boolean.class).ifPresent(v -> properties.put("spring.cloud.config.fail-fast", v));
        values.get("read-timeout", String.class).ifPresent(v -> properties.put("micronaut.http.services.springcloudconfig.read-timeout", v));
        values.get("connect-timeout", String.class).ifPresent(v -> properties.put("micronaut.http.services.springcloudconfig.connect-timeout", v));

        return new SpringCloudImport(null, properties, applicationName, profiles, (String) properties.get(LABEL), values.get("optional", Boolean.class).orElse(false), retryPolicy);
    }

    @Override
    protected Optional<PropertySource> importRetryablePropertySource(ImportContext<SpringCloudImport> context) {
        SpringCloudImport declaration = context.importDeclaration();
        if (declaration.applicationName() == null || declaration.profiles() == null) {
            return Optional.empty();
        }
        Map<String, Object> properties = declaration.properties();

        ApplicationContext importerContext = getOrCreateContext(properties);
        Map<String, Object> imported = importSupport.load(importerContext, declaration.applicationName(), declaration.profiles(), declaration.label(), declaration.optional());
        if (imported.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PropertySource.of(context.getCanonicalLocation(), imported, EnvironmentPropertySource.POSITION + 100));
    }

    @Override
    protected void closeRetryableImporter() {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
            cachedContextProperties = null;
        }
    }

    private Map<String, Object> buildContextProperties(ConnectionString connectionString) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(SpringCloudClientConfiguration.PREFIX + ".uri", "http://" + connectionString.getHosts().get(0).host() + ':' + connectionString.getHosts().get(0).port());
        properties.putAll(optionBinder.bind(connectionString));
        return properties;
    }

    private ApplicationContext getOrCreateContext(Map<String, Object> properties) {
        if (applicationContext == null || !properties.equals(cachedContextProperties)) {
            close();
            cachedContextProperties = new LinkedHashMap<>(properties);
            applicationContext = contextFactory.build(properties);
        }
        return applicationContext;
    }

    /**
     * Typed Spring Cloud Config import declaration.
     *
     * @param connectionString The parsed import connection string, if available
     * @param properties The importer child-context properties
     * @param applicationName The target application name
     * @param profiles The target profile list
     * @param label The optional Config Server label
     * @param optional Whether the import is optional
     * @param retryPolicy The resolved import retry policy
     */
    public record SpringCloudImport(ConnectionString connectionString,
                                    Map<String, Object> properties,
                                    String applicationName,
                                    String profiles,
                                    String label,
                                    boolean optional,
                                    RetryPolicy retryPolicy) {
    }
}
