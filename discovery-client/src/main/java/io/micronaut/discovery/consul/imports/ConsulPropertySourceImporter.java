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
import io.micronaut.context.env.PropertySourceImporter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.imports.RemoteConfigImporterContextFactory;
import io.micronaut.discovery.imports.RemoteConfigImportMetadata;
import io.micronaut.discovery.imports.RemoteConfigImportOptionBinder;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.consul.watch.WatchConfiguration;

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
public final class ConsulPropertySourceImporter implements PropertySourceImporter {

    private final RemoteConfigImportOptionBinder optionBinder = new RemoteConfigImportOptionBinder();
    private final RemoteConfigImporterContextFactory contextFactory = new RemoteConfigImporterContextFactory();
    private final ConsulImportSupport importSupport = new ConsulImportSupport();
    private ApplicationContext applicationContext;
    private Map<String, Object> cachedContextProperties;

    @Override
    public String getProtocol() {
        return "consul";
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext context) {
        Map<String, Object> properties = buildContextProperties(context);

        String format = String.valueOf(properties.getOrDefault(ConsulConfiguration.PREFIX + ".config.format", ConfigDiscoveryConfiguration.Format.NATIVE.name().toLowerCase(Locale.ENGLISH)));
        String datacenter = (String) properties.get(ConsulConfiguration.PREFIX + ".config.datacenter");

        ApplicationContext importerContext = getOrCreateContext(properties);
        ConnectionString connectionString = context.connectionString();
        Map<String, Object> imported = importSupport.load(importerContext, connectionString.getPath(), format, datacenter);
        if (imported.isEmpty()) {
            return Optional.empty();
        }
        boolean watchEnabled = Boolean.parseBoolean(connectionString.getOptions().getOrDefault("watch", "false"));
        String propertySourceName = watchEnabled ? connectionString.getPath() : context.getCanonicalLocation();
        if (watchEnabled) {
            imported.put(WatchConfiguration.PREFIX + ".enabled", true);
            ConnectionString.HostPort hostPort = connectionString.getHosts().getFirst();
            imported.put(ConsulConfiguration.PREFIX + ".host", hostPort.host());
            if (hostPort.port() != null) {
                imported.put(ConsulConfiguration.PREFIX + ".port", hostPort.port());
            }
            imported.put(RemoteConfigImportMetadata.CONSUL_WATCH_ENABLED, true);
            importSupport.resolveWatchPath(connectionString.getPath(), format, imported)
                .ifPresent(watchPath -> imported.put(RemoteConfigImportMetadata.CONSUL_WATCH_PATH, watchPath));
            imported.put(RemoteConfigImportMetadata.CONSUL_WATCH_FORMAT, format.toUpperCase(Locale.ENGLISH));
            imported.put(RemoteConfigImportMetadata.CONSUL_WATCH_PROPERTY_SOURCE, context.getCanonicalLocation());
        }
        return Optional.of(PropertySource.of(propertySourceName, imported, EnvironmentPropertySource.POSITION + 100));
    }

    @Override
    public void close() {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
            cachedContextProperties = null;
        }
    }

    private Map<String, Object> buildContextProperties(ImportContext context) {
        Map<String, Object> properties = new LinkedHashMap<>();
        ConnectionString.HostPort hostPort = context.connectionString().getHosts().getFirst();
        properties.put(ConsulConfiguration.PREFIX + ".host", hostPort.host());
        if (hostPort.port() != null) {
            properties.put(ConsulConfiguration.PREFIX + ".port", hostPort.port());
        }
        properties.putAll(optionBinder.bind(context.connectionString()));
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
}
