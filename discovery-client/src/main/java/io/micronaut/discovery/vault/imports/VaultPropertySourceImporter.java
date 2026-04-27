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

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.discovery.config.RetryablePropertySourceImporter;
import io.micronaut.discovery.imports.RemoteConfigImporterContextFactory;
import io.micronaut.discovery.imports.RemoteConfigImportOptionBinder;
import io.micronaut.discovery.vault.config.VaultClientConfiguration;
import io.micronaut.retry.RetryPolicy;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.EnvironmentPropertySource;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Property source importer for explicit Vault secret paths.
 */
@Internal
public final class VaultPropertySourceImporter extends RetryablePropertySourceImporter<VaultPropertySourceImporter.VaultImport> {

    private static final int DEFAULT_PORT = 8200;

    private final RemoteConfigImportOptionBinder optionBinder = new RemoteConfigImportOptionBinder();
    private final RemoteConfigImporterContextFactory contextFactory = new RemoteConfigImporterContextFactory();
    private final VaultImportSupport importSupport = new VaultImportSupport();
    private @Nullable ApplicationContext applicationContext;
    private @Nullable Map<String, Object> cachedContextProperties;

    @Override
    public String getProvider() {
        return "vault";
    }

    @Override
    protected VaultImport newImportDeclaration(ConnectionString connectionString, RetryPolicy retryPolicy) {
        return new VaultImport(
            connectionString,
            buildContextProperties(connectionString),
            connectionString.getPath(),
            connectionString.isOptional(),
            retryPolicy
        );
    }

    @Override
    protected VaultImport newImportDeclaration(ConvertibleValues<Object> values, RetryPolicy retryPolicy) {
        String uri = values.get("uri", String.class)
            .or(() -> values.get("url", String.class))
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new ConfigurationException("Config import provider [vault] requires non-blank ['uri']"));
        String secretPath = values.get("path", String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new ConfigurationException("Config import provider [vault] requires non-blank ['path']"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(VaultClientConfiguration.PREFIX + ".uri", uri);
        values.get("token", String.class).ifPresent(v -> properties.put("vault.client.token", v));
        values.get("kv-version", String.class).ifPresent(v -> properties.put("vault.client.kv-version", v));
        values.get("secret-engine-name", String.class).ifPresent(v -> properties.put("vault.client.secret-engine-name", v));
        values.get("path-prefix", String.class).ifPresent(v -> properties.put("vault.client.path-prefix", v));
        values.get("fail-fast", Boolean.class).ifPresent(v -> properties.put("vault.client.fail-fast", v));
        values.get("read-timeout", String.class).ifPresent(v -> properties.put("micronaut.http.services.vault.read-timeout", v));
        values.get("connect-timeout", String.class).ifPresent(v -> properties.put("micronaut.http.services.vault.connect-timeout", v));

        return new VaultImport(null, properties, secretPath, values.get("optional", Boolean.class).orElse(false), retryPolicy);
    }

    @Override
    protected Optional<PropertySource> importRetryablePropertySource(ImportContext<VaultImport> context) {
        VaultImport declaration = context.importDeclaration();
        Map<String, Object> properties = declaration.properties();

        ApplicationContext importerContext = getOrCreateContext(properties);
        Map<String, Object> imported = importSupport.load(importerContext, declaration.secretPath(), declaration.optional());
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
        properties.put(VaultClientConfiguration.PREFIX + ".uri", buildUri(connectionString));
        properties.putAll(optionBinder.bind(connectionString));
        return properties;
    }

    private String buildUri(ConnectionString connectionString) {
        ConnectionString.HostPort hostPort = connectionString.getHosts().getFirst();
        Integer port = hostPort.port();
        return "http://" + hostPort.host() + ':' + (port == null ? DEFAULT_PORT : port);
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
     * Typed Vault import declaration.
     *
     * @param connectionString The parsed import connection string, if available
     * @param properties The importer child-context properties
     * @param secretPath The explicit Vault secret path
     * @param optional Whether the import is optional
     * @param retryPolicy The resolved import retry policy
     */
    public record VaultImport(@Nullable ConnectionString connectionString,
                              Map<String, Object> properties,
                              String secretPath,
                              boolean optional,
                              RetryPolicy retryPolicy) {
    }
}
