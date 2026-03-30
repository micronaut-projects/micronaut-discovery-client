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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ConnectionString;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Binds shared and provider-specific query parameters from distributed config import URIs.
 */
@Internal
public final class RemoteConfigImportOptionBinder {

    public static final String FORMAT_NATIVE = "native";
    public static final String FORMAT_JSON = "json";
    public static final String FORMAT_YAML = "yaml";
    public static final String FORMAT_YML = "yml";
    public static final String FORMAT_PROPERTIES = "properties";
    public static final String FORMAT_FILE = "file";

    private static final Map<String, String> COMMON_OPTIONS = Map.of(
        "retry-attempts", "retry-attempts",
        "retry-count", "retry-attempts",
        "retry-delay", "retry-delay",
        "read-timeout", "read-timeout",
        "connect-timeout", "connect-timeout"
    );

    private static final Map<String, Map<String, String>> PROVIDER_OPTIONS = Map.of(
        "consul", Map.of(
            "format", "consul.client.config.format",
            "dc", "consul.client.config.datacenter",
            "acl-token", "consul.client.asl-token",
            "fail-fast", "consul.client.config.fail-fast",
            "watch", "micronaut.discovery.consul.import.watch"
        ),
        "vault", Map.of(
            "token", "vault.client.token",
            "kv-version", "vault.client.kv-version",
            "secret-engine-name", "vault.client.secret-engine-name",
            "path-prefix", "vault.client.path-prefix",
            "fail-fast", "vault.client.fail-fast"
        ),
        "springcloud", Map.of(
            "label", "spring.cloud.config.label",
            "username", "spring.cloud.config.username",
            "password", "spring.cloud.config.password",
            "fail-fast", "spring.cloud.config.fail-fast"
        )
    );

    public Map<String, Object> bind(ConnectionString connectionString) {
        String protocol = connectionString.protocol();
        Map<String, String> providerOptions = PROVIDER_OPTIONS.get(protocol);
        if (providerOptions == null) {
            throw new ConfigurationException("Unsupported import protocol: " + protocol);
        }

        Map<String, Object> bound = new LinkedHashMap<>();
        bindUserInfo(connectionString, protocol, bound);
        for (Map.Entry<String, String> option : connectionString.options().entrySet()) {
            String key = option.getKey();
            String normalizedCommon = COMMON_OPTIONS.get(key);
            if (normalizedCommon != null) {
                if (!bound.containsKey(normalizedCommon) || "retry-attempts".equals(key)) {
                    bound.put(normalizedCommon, option.getValue());
                }
                continue;
            }

            String propertyName = providerOptions.get(key);
            if (propertyName == null) {
                throw unsupportedOption(connectionString, key, providerOptions.keySet());
            }
            bound.put(propertyName, option.getValue());
        }

        return bound;
    }

    private void bindUserInfo(ConnectionString connectionString, String protocol, Map<String, Object> bound) {
        String username = connectionString.getUsername().orElse(null);
        String password = connectionString.getPassword().orElse(null);
        switch (protocol) {
            case "consul" -> {
                if (username != null && !username.isEmpty()) {
                    bound.put("consul.client.asl-token", username);
                }
            }
            case "vault" -> {
                if (username != null && !username.isEmpty()) {
                    bound.put("vault.client.token", username);
                }
            }
            case "springcloud" -> {
                if (username != null && !username.isEmpty()) {
                    bound.put("spring.cloud.config.username", username);
                }
                if (password != null && !password.isEmpty()) {
                    bound.put("spring.cloud.config.password", password);
                }
            }
            default -> {
            }
        }
    }

    private static ConfigurationException unsupportedOption(ConnectionString connectionString, String option, Set<String> supportedOptions) {
        return new ConfigurationException(
            "Unsupported query parameter '" + option + "' for import protocol '" + connectionString.protocol() + "'. Supported provider options: " + supportedOptions
        );
    }
}
