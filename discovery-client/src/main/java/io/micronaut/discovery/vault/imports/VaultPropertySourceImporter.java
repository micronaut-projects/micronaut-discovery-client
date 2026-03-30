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
import io.micronaut.context.env.PropertySourceImporter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.imports.RemoteConfigImporterContextFactory;
import io.micronaut.discovery.imports.RemoteConfigImportOptionBinder;
import io.micronaut.discovery.vault.config.VaultClientConfiguration;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.EnvironmentPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Property source importer for explicit Vault secret paths.
 */
@Internal
public final class VaultPropertySourceImporter implements PropertySourceImporter {

    private final RemoteConfigImportOptionBinder optionBinder = new RemoteConfigImportOptionBinder();
    private final RemoteConfigImporterContextFactory contextFactory = new RemoteConfigImporterContextFactory();
    private final VaultImportSupport importSupport = new VaultImportSupport();
    private ApplicationContext applicationContext;
    private Map<String, Object> cachedContextProperties;

    @Override
    public String getProtocol() {
        return "vault";
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext context) {
        Map<String, Object> properties = buildContextProperties(context);

        ApplicationContext importerContext = getOrCreateContext(properties);
        Map<String, Object> imported = importSupport.load(importerContext, context.connectionString().getPath(), context.connectionString().isOptional());
        if (imported.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PropertySource.of(context.getCanonicalLocation(), imported, EnvironmentPropertySource.POSITION + 100));
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
        properties.put(VaultClientConfiguration.PREFIX + ".uri", "http://" + context.connectionString().getHosts().get(0).host() + ':' + context.connectionString().getHosts().get(0).port());
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
