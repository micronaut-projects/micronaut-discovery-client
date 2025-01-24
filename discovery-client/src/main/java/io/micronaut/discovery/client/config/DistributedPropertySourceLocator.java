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
package io.micronaut.discovery.client.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigurationClient;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * <p>A {@link BootstrapPropertySourceLocator} implementation that uses the {@link ConfigurationClient} to find
 * available {@link PropertySource} instances from distributed configuration sources.</p>
 * <p>
 * <p>This implementation using a Blocking operation which is required during bootstrap which is configured to Timeout after
 * 10 seconds. The timeout can be configured with {@code micronaut.config.readTimeout} in configuration</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(property = ConfigurationClient.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@BootstrapContextCompatible
public class DistributedPropertySourceLocator implements BootstrapPropertySourceLocator {
    private static final Logger LOG = LoggerFactory.getLogger(DistributedPropertySourceLocator.class);
    private final ConfigurationClient configurationClient;
    private final List<BlockingConfigurationClient> configurationClients;
    private final Duration readTimeout;

    /**
     * @param configurationClient The configuration client
     * @param readTimeout         The read timeout
     * @deprecated Use {@link #DistributedPropertySourceLocator(ConfigurationClient, Duration)} instead
     */
    @Deprecated(forRemoval = true, since = "4.6.0")
    public DistributedPropertySourceLocator(
        ConfigurationClient configurationClient,
        @Value("${" + ConfigurationClient.READ_TIMEOUT + ":10s}")
            Duration readTimeout) {
        this(configurationClient, readTimeout, Collections.emptyList());
    }

    /**
     * @param configurationClient Reactive configuration client
     * @param readTimeout         The read timeout
     * @param configurationClients configuration clients
     */
    @Inject
    public DistributedPropertySourceLocator(
        @Nullable  ConfigurationClient configurationClient,
         @Value("${" + ConfigurationClient.READ_TIMEOUT + ":10s}") Duration readTimeout,
        @NonNull List<BlockingConfigurationClient> configurationClients) {
        this.configurationClient = configurationClient;
        this.readTimeout = readTimeout;
        this.configurationClients = configurationClients;
    }

    @Override
    @Blocking
    public Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving configuration sources from client: {}", configurationClient);
        }
        try {
            Flux<PropertySource> propertySourceFlowable = Flux.from(configurationClient.getPropertySources(environment));
            List<PropertySource> propertySources = propertySourceFlowable
                .timeout(Duration.ofMillis(readTimeout.toMillis()))
                .collectList()
                .block();
            if (propertySources == null) {
                propertySources = new ArrayList<>();
            }
            if (LOG.isInfoEnabled()) {
                LOG.info("Resolved {} configuration sources from client: {}", propertySources.size(), configurationClient);
            }
            for (BlockingConfigurationClient cc : configurationClients) {
                propertySources.addAll(cc.getPropertySources(environment));
            }
            return propertySources;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new ConfigurationException("Read timeout occurred reading distributed configuration from client: " + configurationClient.getDescription(), e);
            } else {
                throw e;
            }
        }
    }
}
