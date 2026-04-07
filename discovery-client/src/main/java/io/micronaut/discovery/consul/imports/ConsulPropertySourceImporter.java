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
package io.micronaut.discovery.consul.imports;

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.imports.RemoteConfigImporterContextFactory;
import io.micronaut.discovery.imports.RemoteConfigImportOptionBinder;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.config.RetryablePropertySourceImporter;
import io.micronaut.discovery.consul.watch.WatchConfiguration;
import io.micronaut.retry.RetryPolicy;

import java.util.LinkedHashMap;
import java.util.Locale;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.EnvironmentPropertySource;
import java.util.Map;
import java.util.Optional;

/**
 * Property source importer for explicit Consul configuration paths.
 */
@Internal
public final class ConsulPropertySourceImporter extends RetryablePropertySourceImporter<ConsulPropertySourceImporter.ConsulImport> {

    private final RemoteConfigImportOptionBinder optionBinder = new RemoteConfigImportOptionBinder();
    private final RemoteConfigImporterContextFactory contextFactory = new RemoteConfigImporterContextFactory();
    private final ConsulImportSupport importSupport = new ConsulImportSupport();
    private ApplicationContext applicationContext;
    private Map<String, Object> cachedContextProperties;

    @Override
    public String getProvider() {
        return "consul";
    }

    @Override
    protected ConsulImport newImportDeclaration(ConnectionString connectionString, RetryPolicy retryPolicy) {
        Map<String, Object> properties = buildContextProperties(connectionString);
        String format = String.valueOf(properties.getOrDefault(ConsulConfiguration.PREFIX + ".config.format", ConfigDiscoveryConfiguration.Format.NATIVE.name().toLowerCase(Locale.ENGLISH)));
        String datacenter = (String) properties.get(ConsulConfiguration.PREFIX + ".config.datacenter");
        boolean watchEnabled = Boolean.parseBoolean(connectionString.getOptions().getOrDefault("watch", "false"));
        return new ConsulImport(properties, format, datacenter, watchEnabled, connectionString.getPath(), connectionString.isOptional(), retryPolicy);
    }

    @Override
    protected ConsulImport newImportDeclaration(ConvertibleValues<Object> values, RetryPolicy retryPolicy) {
        String host = values.get("host", String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new ConfigurationException("Config import provider [consul] requires non-blank ['host']"));
        Integer port = values.get("port", Integer.class).orElse(8500);
        String path = values.get("path", String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new ConfigurationException("Config import provider [consul] requires non-blank ['path']"));
        String format = values.get("format", String.class)
            .orElse(ConfigDiscoveryConfiguration.Format.NATIVE.name().toLowerCase(Locale.ENGLISH));
        String datacenter = values.get("dc", String.class).orElse(null);
        boolean watchEnabled = values.get("watch", Boolean.class).orElse(false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(ConsulConfiguration.PREFIX + ".host", host);
        properties.put(ConsulConfiguration.PREFIX + ".port", port);
        properties.put(ConsulConfiguration.PREFIX + ".config.format", format);
        if (datacenter != null) {
            properties.put(ConsulConfiguration.PREFIX + ".config.datacenter", datacenter);
        }
        values.get("acl-token", String.class).ifPresent(v -> properties.put("consul.client.acl-token", v));
        values.get("fail-fast", Boolean.class).ifPresent(v -> properties.put("consul.client.fail-fast", v));
        values.get("read-timeout", String.class).ifPresent(v -> properties.put("micronaut.http.services.consul.read-timeout", v));
        values.get("connect-timeout", String.class).ifPresent(v -> properties.put("micronaut.http.services.consul.connect-timeout", v));

        return new ConsulImport(properties, format, datacenter, watchEnabled, path, values.get("optional", Boolean.class).orElse(false), retryPolicy);
    }

    @Override
    protected Optional<PropertySource> importRetryablePropertySource(ImportContext<ConsulImport> context) {
        ConsulImport declaration = context.importDeclaration();
        Map<String, Object> properties = declaration.properties();
        String format = declaration.format();
        String datacenter = declaration.datacenter();

        ApplicationContext importerContext = getOrCreateContext(properties);
        String importPath = declaration.path();
        Map<String, Object> imported = importSupport.load(importerContext, importPath, format, datacenter);
        if (imported.isEmpty()) {
            return Optional.empty();
        }
        boolean watchEnabled = declaration.watchEnabled();
        String propertySourceName = watchEnabled ? importPath : (context.connectionString() != null ? context.getCanonicalLocation() : getProvider() + "://" + importPath);
        if (watchEnabled) {
            String watchPath = importSupport.resolveWatchPath(importPath, format, imported).orElse(null);
            imported.put(WatchConfiguration.PREFIX + ".enabled", true);
            imported.putAll(properties.entrySet().stream()
                .filter(e -> e.getKey().equals(ConsulConfiguration.PREFIX + ".host") || e.getKey().equals(ConsulConfiguration.PREFIX + ".port"))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            if (watchPath != null) {
                imported.put(WatchConfiguration.IMPORTED_PATHS, watchPath);
            }
            imported.put(WatchConfiguration.IMPORTED_FORMAT, format.toUpperCase(Locale.ENGLISH));
        }
        return Optional.of(PropertySource.of(propertySourceName, imported, EnvironmentPropertySource.POSITION + 100));
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
        ConnectionString.HostPort hostPort = connectionString.getHosts().getFirst();
        properties.put(ConsulConfiguration.PREFIX + ".host", hostPort.host());
        if (hostPort.port() != null) {
            properties.put(ConsulConfiguration.PREFIX + ".port", hostPort.port());
        }
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
     * Typed Consul import declaration.
     *
     * @param properties The importer child-context properties
     * @param format The Consul config format
     * @param datacenter The optional Consul datacenter
     * @param watchEnabled Whether importer-driven watch refresh is enabled
     * @param path The explicit Consul import path
     * @param optional Whether the import is optional
     * @param retryPolicy The resolved import retry policy
     */
    public record ConsulImport(Map<String, Object> properties,
                               String format,
                               String datacenter,
                               boolean watchEnabled,
                               String path,
                               boolean optional,
                               RetryPolicy retryPolicy) {
    }
}
