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
package io.micronaut.discovery.consul.watch;

import static io.micronaut.context.env.Environment.DEFAULT_NAME;
import static io.micronaut.discovery.config.ConfigDiscoveryConfiguration.DEFAULT_PATH;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Singleton;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueriesConsulClient;
import io.micronaut.jackson.core.env.JsonPropertySourceLoader;

/**
 * Create needed Consul {@link Watcher} based on configuration.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@Factory
public final class WatchFactory {

    private static final String CONSUL_PATH_SEPARATOR = "/";

    private final Environment environment;
    private final BlockedQueriesConsulClient consulClient;
    private final BlockingQueriesConfiguration blockingQueriesConfiguration;
    private final PropertiesChangeHandler propertiesChangeHandler;

    public WatchFactory(final Environment environment,
                        final BlockedQueriesConsulClient consulClient,
                        final BlockingQueriesConfiguration blockingQueriesConfiguration,
                        final PropertiesChangeHandler propertiesChangeHandler) {
        this.environment = environment;
        this.consulClient = consulClient;
        this.blockingQueriesConfiguration = blockingQueriesConfiguration;
        this.propertiesChangeHandler = propertiesChangeHandler;
    }

    @Singleton
    Watcher createWatcher(final ConsulConfiguration consulConfiguration) {
        final var kvPaths = computeKvPaths(consulConfiguration);

        final var format = consulConfiguration.getConfiguration().getFormat();

        return switch (format) {
            case NATIVE -> watchNative(kvPaths);
            case JSON -> watchConfigurations(kvPaths, new JsonPropertySourceLoader());
            case YAML -> watchConfigurations(kvPaths, new YamlPropertySourceLoader());
            case PROPERTIES -> watchConfigurations(kvPaths, new PropertiesPropertySourceLoader());
            default ->
                throw new ConfigurationException("Unhandled configuration format: " + format);
        };
    }

    List<String> computeKvPaths(final ConsulConfiguration consulConfiguration) {
        final var applicationName = consulConfiguration.getServiceId().orElseThrow();
        final var configurationPath = getConfigurationPath(consulConfiguration);

        final var kvPaths = new ArrayList<String>();
        // Configuration shared by all applications
        final var commonConfigPath = configurationPath + DEFAULT_NAME;
        kvPaths.add(commonConfigPath);

        // Application-specific configuration
        final var applicationSpecificPath = configurationPath + applicationName;
        kvPaths.add(applicationSpecificPath);

        for (final var activeName : environment.getActiveNames()) {
            // Configuration shared by all applications by active environments
            kvPaths.add(toProfiledPath(commonConfigPath, activeName));
            // Application-specific configuration by active environments
            kvPaths.add(toProfiledPath(applicationSpecificPath, activeName));
        }

        return kvPaths;
    }

    private static String getConfigurationPath(final ConsulConfiguration consulConfiguration) {
        return consulConfiguration.getConfiguration().getPath()
            .map(path -> {
                if (!path.endsWith(CONSUL_PATH_SEPARATOR)) {
                    path += CONSUL_PATH_SEPARATOR;
                }

                return path;
            })
            .orElse(DEFAULT_PATH);
    }

    private static String toProfiledPath(final String resource, final String activeName) {
        return resource + "," + activeName;
    }

    private Watcher watchNative(final List<String> keyPaths) {
        // adding '/' at the end of the kvPath to distinct 'kvPath/' from 'kvPath,profile/'
        final var kvPaths = keyPaths.stream().map(path -> path + CONSUL_PATH_SEPARATOR).toList();
        return new NativeWatcher(kvPaths, consulClient, blockingQueriesConfiguration, propertiesChangeHandler);
    }

    private Watcher watchConfigurations(final List<String> kvPaths,
                                        final PropertySourceLoader propertySourceLoader) {
        return new ConfigurationsWatcher(kvPaths, consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceLoader);
    }

}
