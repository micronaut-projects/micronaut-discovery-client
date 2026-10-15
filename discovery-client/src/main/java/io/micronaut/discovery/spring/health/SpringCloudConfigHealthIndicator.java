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
package io.micronaut.discovery.spring.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.spring.config.SpringCloudClientConfiguration;
import io.micronaut.discovery.spring.config.SpringCloudConfigurationClient;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.discovery.spring.config.client.SpringCloudConfigClient;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link HealthIndicator} for Spring Cloud Config Server.
 *
 * <p>Mirrors Spring Cloud Config's client side {@code ConfigServerHealthIndicator}: the environment of
 * the application is fetched from the config server with the configured application name, active
 * environments and label, and the names of the located property sources are reported as details. The
 * result is cached for {@link SpringCloudClientConfiguration#getHealthCheckTimeToLive()} so that health
 * probes do not fetch the whole environment on every request.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Requires(classes = HealthIndicator.class)
@Requires(beans = {SpringCloudClientConfiguration.class, SpringCloudConfigClient.class})
@Requires(property = SpringCloudClientConfiguration.PREFIX + ".health-check", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class SpringCloudConfigHealthIndicator implements HealthIndicator {

    /**
     * The name of the health result.
     */
    public static final String NAME = "spring-cloud-config";

    private static final String DEFAULT_APPLICATION = "application";

    private final SpringCloudConfigClient client;
    private final SpringCloudClientConfiguration configuration;
    private final ApplicationConfiguration applicationConfiguration;
    private final Environment environment;
    private final Mono<HealthResult> result;

    /**
     * @param client                   The Spring Cloud Config client
     * @param configuration            The Spring Cloud Config client configuration
     * @param applicationConfiguration The application configuration
     * @param environment              The environment
     */
    public SpringCloudConfigHealthIndicator(SpringCloudConfigClient client,
                                            SpringCloudClientConfiguration configuration,
                                            ApplicationConfiguration applicationConfiguration,
                                            Environment environment) {
        this.client = client;
        this.configuration = configuration;
        this.applicationConfiguration = applicationConfiguration;
        this.environment = environment;
        Mono<HealthResult> check = Mono.defer(this::check);
        Duration timeToLive = configuration.getHealthCheckTimeToLive();
        this.result = timeToLive.isZero() || timeToLive.isNegative() ? check : check.cache(timeToLive);
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return result;
    }

    private Mono<HealthResult> check() {
        String applicationName = configuration.getName()
            .orElseGet(() -> applicationConfiguration.getName().orElse(DEFAULT_APPLICATION));
        String profiles = Optional.ofNullable(StringUtils.trimToNull(String.join(",", environment.getActiveNames())))
            .orElse(SpringCloudConfigurationClient.DEFAULT_PROFILE);
        String label = configuration.getLabel();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", applicationName);
        details.put("profiles", profiles);
        if (label != null) {
            details.put("label", label);
        }
        return Mono.from(read(applicationName, profiles, label))
            .map(response -> up(details, response))
            .switchIfEmpty(Mono.fromSupplier(() -> HealthResult.builder(NAME, HealthStatus.UP)
                .details(withPropertySources(details, List.of()))
                .build()))
            .onErrorResume(throwable -> Mono.just(HealthResult.builder(NAME, HealthStatus.DOWN)
                .exception(throwable)
                .build()));
    }

    private Publisher<ConfigServerResponse> read(String applicationName, String profiles, @Nullable String label) {
        String authorization = authorization();
        if (authorization == null) {
            return label == null
                ? client.readValues(applicationName, profiles)
                : client.readValues(applicationName, profiles, label);
        }
        return label == null
            ? client.readValuesAuthorized(applicationName, profiles, authorization)
            : client.readValuesAuthorized(applicationName, profiles, label, authorization);
    }

    private @Nullable String authorization() {
        Optional<String> username = configuration.getUsername();
        Optional<String> password = configuration.getPassword();
        if (username.isPresent() && password.isPresent()) {
            String basicAuth = username.get() + ':' + password.get();
            return "Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    private static HealthResult up(Map<String, Object> requestDetails, ConfigServerResponse response) {
        Map<String, Object> details = new LinkedHashMap<>(requestDetails);
        if (response.getVersion() != null) {
            details.put("version", response.getVersion());
        }
        if (response.getState() != null) {
            details.put("state", response.getState());
        }
        List<String> propertySources = new ArrayList<>();
        if (response.getPropertySources() != null) {
            for (ConfigServerPropertySource propertySource : response.getPropertySources()) {
                propertySources.add(propertySource.getName());
            }
        }
        return HealthResult.builder(NAME, HealthStatus.UP)
            .details(withPropertySources(details, propertySources))
            .build();
    }

    private static Map<String, Object> withPropertySources(Map<String, Object> details, List<String> propertySources) {
        Map<String, Object> result = new LinkedHashMap<>(details);
        result.put("property-sources", propertySources);
        return result;
    }
}
