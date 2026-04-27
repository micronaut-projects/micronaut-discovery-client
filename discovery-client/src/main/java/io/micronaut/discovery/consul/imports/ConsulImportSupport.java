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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.KeyValue;
import io.micronaut.discovery.imports.RemoteConfigImportOptionBinder;
import io.micronaut.discovery.imports.RemoteConfigImportMetadata;
import io.micronaut.jackson.core.env.JsonPropertySourceLoader;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads Consul configuration values for explicit import paths.
 */
@Internal
final class ConsulImportSupport {

    public Map<String, Object> load(ApplicationContext context, String path, String format, @Nullable String datacenter) {
        ConsulClient consulClient = context.getBean(ConsulClient.class);
        List<KeyValue> keyValues = Flux.from(consulClient.readValues(path, datacenter, null, null)).blockFirst();
        if (keyValues == null || keyValues.isEmpty()) {
            return Map.of();
        }

        return switch (format.toLowerCase(Locale.ENGLISH)) {
            case RemoteConfigImportOptionBinder.FORMAT_NATIVE -> asNativeMap(path, keyValues);
            case RemoteConfigImportOptionBinder.FORMAT_JSON -> decodeWithLoader(new JsonPropertySourceLoader(), path, keyValues);
            case RemoteConfigImportOptionBinder.FORMAT_YAML, RemoteConfigImportOptionBinder.FORMAT_YML -> decodeWithLoader(new YamlPropertySourceLoader(), path, keyValues);
            case RemoteConfigImportOptionBinder.FORMAT_PROPERTIES -> decodeWithLoader(new PropertiesPropertySourceLoader(), path, keyValues);
            case RemoteConfigImportOptionBinder.FORMAT_FILE -> asFileMap(path, keyValues);
            default -> throw new ConfigurationException("Unsupported consul import format: " + format);
        };
    }

    public Optional<String> resolveWatchPath(String path, String format, Map<String, Object> importedValues) {
        if (!RemoteConfigImportOptionBinder.FORMAT_NATIVE.equalsIgnoreCase(format) || importedValues.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private Map<String, Object> asNativeMap(String path, List<KeyValue> keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        String prefix = path.endsWith("/") ? path : path + '/';
        Base64.Decoder decoder = Base64.getDecoder();
        for (KeyValue keyValue : keyValues) {
            if (keyValue.getKey() != null && keyValue.getKey().startsWith(prefix) && keyValue.getValue() != null) {
                values.put(keyValue.getKey().substring(prefix.length()), new String(decoder.decode(keyValue.getValue())));
            }
        }
        return values;
    }

    private Map<String, Object> asFileMap(String path, List<KeyValue> keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (KeyValue keyValue : keyValues) {
            String key = keyValue.getKey();
            if (key != null && keyValue.getValue() != null) {
                int extensionIndex = key.lastIndexOf('.');
                if (extensionIndex > -1) {
                    String extension = key.substring(extensionIndex + 1);
                    PropertySourceLoader loader = switch (extension) {
                        case RemoteConfigImportOptionBinder.FORMAT_JSON -> new JsonPropertySourceLoader();
                        case RemoteConfigImportOptionBinder.FORMAT_YAML, RemoteConfigImportOptionBinder.FORMAT_YML -> new YamlPropertySourceLoader();
                        case RemoteConfigImportOptionBinder.FORMAT_PROPERTIES -> new PropertiesPropertySourceLoader();
                        default -> null;
                    };
                    if (loader != null) {
                        values.putAll(loader.read(path, Base64.getDecoder().decode(keyValue.getValue())));
                    }
                }
            }
        }
        return values;
    }

    private Map<String, Object> decodeWithLoader(PropertySourceLoader loader, String name, List<KeyValue> keyValues) {
        Base64.Decoder decoder = Base64.getDecoder();
        for (KeyValue keyValue : keyValues) {
            if (keyValue.getValue() != null) {
                return loader.read(name, decoder.decode(keyValue.getValue()));
            }
        }
        return Map.of();
    }

    public static PropertySource watchMetadata(String propertySourceName, String watchPath, String format) {
        return PropertySource.of(propertySourceName + "-watch", Map.of(
            RemoteConfigImportMetadata.CONSUL_WATCH_ENABLED, true,
            RemoteConfigImportMetadata.CONSUL_WATCH_PATH, watchPath,
            RemoteConfigImportMetadata.CONSUL_WATCH_FORMAT, format
        ), 101);
    }
}
