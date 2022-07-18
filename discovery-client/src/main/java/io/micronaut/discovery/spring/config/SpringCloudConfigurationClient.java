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
package io.micronaut.discovery.spring.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.spring.config.client.SpringCloudConfigClient;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * A {@link ConfigurationClient} for Spring Cloud client.
 *
 * @author Thiago Locatelli
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@BootstrapContextCompatible
@Requires(beans = SpringCloudClientConfiguration.class)
public class SpringCloudConfigurationClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(SpringCloudConfigurationClient.class);

    private final SpringCloudConfigClient springCloudConfigClient;
    private final SpringCloudClientConfiguration springCloudConfiguration;
    private final ApplicationConfiguration applicationConfiguration;
    private ExecutorService executionService;

    /**
     * @param springCloudConfigClient  The Spring Cloud client
     * @param springCloudConfiguration The Spring Cloud configuration
     * @param applicationConfiguration The application configuration
     * @param executionService         The executor service to use
     */
    protected SpringCloudConfigurationClient(SpringCloudConfigClient springCloudConfigClient,
                                             SpringCloudClientConfiguration springCloudConfiguration,
                                             ApplicationConfiguration applicationConfiguration,
                                             @Named(TaskExecutors.IO) @Nullable ExecutorService executionService) {

        this.springCloudConfigClient = springCloudConfigClient;
        this.springCloudConfiguration = springCloudConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.executionService = executionService;
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!springCloudConfiguration.getConfiguration().isEnabled()) {
            return Flux.empty();
        }

        Optional<String> configuredApplicationName = applicationConfiguration.getName();
        if (!configuredApplicationName.isPresent()) {
            return Flux.empty();
        } else {
            String applicationName = springCloudConfiguration.getName().orElse(configuredApplicationName.get());
            Set<String> activeNames = environment.getActiveNames();
            String profiles = StringUtils.trimToNull(String.join(",", activeNames));

            if (LOG.isDebugEnabled()) {
                LOG.debug("Spring Cloud Config Active: {}", springCloudConfiguration.getUri());
                LOG.debug("Application Name: {}, Application Profiles: {}, label: {}", applicationName, profiles,
                         springCloudConfiguration.getLabel());
            }

            String authorization = getAuthorization(springCloudConfiguration);
            Publisher<ConfigServerResponse> responsePublisher;

            if (authorization == null) {
                responsePublisher =
                    springCloudConfiguration.getLabel() == null ?
                        springCloudConfigClient.readValues(applicationName, profiles) :
                        springCloudConfigClient.readValues(applicationName,
                            profiles, springCloudConfiguration.getLabel());
            } else {
                responsePublisher =
                    springCloudConfiguration.getLabel() == null ?
                        springCloudConfigClient.readValuesAuthorized(applicationName, profiles, authorization) :
                        springCloudConfigClient.readValuesAuthorized(applicationName,
                            profiles, springCloudConfiguration.getLabel(), authorization);
            }

            Flux<PropertySource> configurationValues = Flux.from(responsePublisher)
                    .onErrorResume(throwable -> {
                        if (throwable instanceof HttpClientResponseException) {
                            HttpClientResponseException httpClientResponseException = (HttpClientResponseException) throwable;
                            if (httpClientResponseException.getStatus() == HttpStatus.NOT_FOUND) {
                                if (springCloudConfiguration.isFailFast()) {
                                    return Flux.error(
                                        new ConfigurationException("Could not locate PropertySource and the fail fast property is set", throwable));
                                } else {
                                    return Flux.empty();
                                }
                            }
                        }
                        return Flux.error(new ConfigurationException("Error reading distributed configuration from Spring Cloud: " + throwable.getMessage(), throwable));
                    })
                    .flatMap(response -> {
                        List<ConfigServerPropertySource> springSources = response.getPropertySources();
                        if (CollectionUtils.isEmpty(springSources)) {
                            return Flux.empty();
                        }
                        int baseOrder = EnvironmentPropertySource.POSITION + 100;
                        List<PropertySource> propertySources = new ArrayList<>(springSources.size());
                        //spring returns the property sources with the highest precedence first
                        //reverse order and increment priority so the last (after reversed) item will
                        //have the highest order
                        for (int i = springSources.size() - 1; i >= 0; i--) {
                            ConfigServerPropertySource springSource = springSources.get(i);
                            propertySources.add(PropertySource.of(springSource.getName(), springSource.getSource(), ++baseOrder));
                        }

                        return Flux.fromIterable(propertySources);
                    });

            if (executionService != null) {
                return configurationValues.subscribeOn(Schedulers.fromExecutor(executionService));
            } else {
                return configurationValues;
            }
        }
    }

    @Override
    public final @NonNull String getDescription() {
        return io.micronaut.discovery.spring.config.client.SpringCloudConfigClient.CLIENT_DESCRIPTION;
    }

    /**
     * Gets Basic authorization from the spring cloud client configuration if both username and password are provided, otherwise returns null.
     * @param springCloudConfiguration the spring cloud client configuration
     * @return the Basic authorization header or null if no credentials are configured for spring cloud client
     */
    private static String getAuthorization(SpringCloudClientConfiguration springCloudConfiguration) {
        String authorization = null;
        if (springCloudConfiguration.getUsername().isPresent() && springCloudConfiguration.getPassword().isPresent()) {
            String basicAuth = springCloudConfiguration.getUsername().get() + ":" + springCloudConfiguration.getPassword().get();
            authorization = "Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes());
        }
        return authorization;
    }
}
