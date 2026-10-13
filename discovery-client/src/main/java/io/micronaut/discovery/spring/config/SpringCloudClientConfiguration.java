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
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import jakarta.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;
import java.util.Optional;

/**
 * A {@link HttpClientConfiguration} for Spring Cloud Config.
 *
 *  @author Thiago Locatelli
 *  @author graemerocher
 *  @since 1.0
 */
@ConfigurationProperties(SpringCloudClientConfiguration.PREFIX)
@BootstrapContextCompatible
@Requires(property = "spring.cloud.config.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class SpringCloudClientConfiguration extends HttpClientConfiguration {

    public static final String PREFIX = "spring.cloud.config";

    /**
     * Default value for Fail fast.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_FAIL_FAST = false;

    /**
     * Default value for whether the config server is considered for health checks.
     * @since 5.3.0
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_HEALTH_CHECK = true;

    /**
     * Default time-to-live of a cached config server health check result (5 minutes).
     * @since 5.3.0
     */
    @SuppressWarnings("WeakerAccess")
    public static final Duration DEFAULT_HEALTH_CHECK_TIME_TO_LIVE = Duration.ofMinutes(5);

    private static final String DEFAULT_URI = "http://localhost:8888";
    public static final String SPRING_CLOUD_CONFIG_ENDPOINT = "${" + SpringCloudClientConfiguration.PREFIX + ".uri:`" + DEFAULT_URI + "`}";

    private String uri = DEFAULT_URI;
    private String label;
    private boolean failFast = DEFAULT_FAIL_FAST;
    private String name;
    private String username;
    private String password;
    private @Nullable String token;
    private @Nullable String bearerToken;
    private Map<String, String> headers = Collections.emptyMap();
    private boolean healthCheck = DEFAULT_HEALTH_CHECK;
    private Duration healthCheckTimeToLive = DEFAULT_HEALTH_CHECK_TIME_TO_LIVE;

    private final SpringCloudConnectionPoolConfiguration springCloudConnectionPoolConfiguration;
    private final SpringConfigDiscoveryConfiguration springConfigDiscoveryConfiguration = new SpringConfigDiscoveryConfiguration();

    /**
     * Default constructor.
     */
    public SpringCloudClientConfiguration() {
        this.springCloudConnectionPoolConfiguration = new SpringCloudConnectionPoolConfiguration();
    }

    /**
     * @param springCloudConnectionPoolConfiguration The connection pool configuration
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public SpringCloudClientConfiguration(SpringCloudConnectionPoolConfiguration springCloudConnectionPoolConfiguration, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.springCloudConnectionPoolConfiguration = springCloudConnectionPoolConfiguration;
    }

    @Override
    public @NonNull ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return springCloudConnectionPoolConfiguration;
    }

    /**
     * @return The configuration discovery configuration
     */
    public @NonNull SpringConfigDiscoveryConfiguration getConfiguration() {
        return springConfigDiscoveryConfiguration;
    }

    /**
     * @return The spring cloud config server uri
     */
    public @NonNull Optional<String> getUri() {
        return uri != null ? Optional.of(uri) : Optional.empty();
    }

    /**
     * Set the Spring Cloud config server uri.  Default value ({@value #DEFAULT_URI}).
     *
     * @param uri Spring Cloud config server uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * @return The spring cloud config server label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return The spring cloud config server name. Default value is read from micronaut.application.name
     */
    public Optional<String> getName() {
        return name != null ? Optional.of(name) : Optional.empty();
    }

    /**
     * @return The spring cloud config username.
     */
    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    /**
     * @return The spring cloud config password.
     */
    public Optional<String> getPassword() {
        return Optional.ofNullable(password);
    }

    /**
     *
     * @return Flag to indicate that failure to connect to Spring Cloud Config is fatal (default false).
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     *
     * If set to true an exception will be thrown if configuration is not found.
     * Default value ({@value #DEFAULT_FAIL_FAST}).
     *
     * @param failFast  flag to fail fast
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * Set the Spring Cloud config server label.
     *
     * @param label Spring Cloud config server label
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Set the Spring Cloud config server name.
     *
     * @param name Spring Cloud config server name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set the Spring cloud config username.
     *
     * @param username Spring Cloud config username
     */
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    /**
     * Set the Spring cloud config password.
     *
     * @param password Spring Cloud config password
     */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    /**
     * @return The token sent to the Spring Cloud Config Server in the {@code X-Config-Token} header, used for example by the Vault backend.
     * @since 5.3.0
     */
    public Optional<String> getToken() {
        return Optional.ofNullable(token);
    }

    /**
     * Set the token sent to the Spring Cloud Config Server in the {@code X-Config-Token} header.
     * The Config Server forwards it to backends such as HashiCorp Vault.
     *
     * @param token The config token
     * @since 5.3.0
     */
    public void setToken(@Nullable String token) {
        this.token = token;
    }

    /**
     * @return The bearer token sent to the Spring Cloud Config Server in the {@code Authorization} header.
     * @since 5.3.0
     */
    public Optional<String> getBearerToken() {
        return Optional.ofNullable(bearerToken);
    }

    /**
     * Set the bearer token sent to the Spring Cloud Config Server as {@code Authorization: Bearer <token>}.
     * Cannot be combined with {@code username}/{@code password} or an {@code Authorization} entry in {@code headers}.
     *
     * @param bearerToken The bearer token
     * @since 5.3.0
     */
    public void setBearerToken(@Nullable String bearerToken) {
        this.bearerToken = bearerToken;
    }

    /**
     * @return The additional headers sent with every request to the Spring Cloud Config Server
     * @since 5.3.0
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Set additional headers sent with every request to the Spring Cloud Config Server.
     *
     * @param headers The headers
     * @since 5.3.0
     */
    public void setHeaders(@Nullable Map<String, String> headers) {
        this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * @return Whether the config server should be considered for health checks.
     * @see io.micronaut.discovery.spring.health.SpringCloudConfigHealthIndicator
     * @since 5.3.0
     */
    public boolean isHealthCheck() {
        return healthCheck;
    }

    /**
     * Sets whether the config server should be considered for health checks. When enabled and
     * {@code micronaut-management} is on the classpath, the health endpoint reports whether the
     * environment of the application can be fetched from the config server.
     * Default value ({@value #DEFAULT_HEALTH_CHECK}).
     *
     * @param healthCheck True if it should
     * @see io.micronaut.discovery.spring.health.SpringCloudConfigHealthIndicator
     * @since 5.3.0
     */
    public void setHealthCheck(boolean healthCheck) {
        this.healthCheck = healthCheck;
    }

    /**
     * @return How long a config server health check result is cached.
     * @since 5.3.0
     */
    public @NonNull Duration getHealthCheckTimeToLive() {
        return healthCheckTimeToLive;
    }

    /**
     * Sets how long a config server health check result is cached before the config server is
     * queried again. A zero or negative duration disables caching. Default value (5 minutes).
     *
     * @param healthCheckTimeToLive The time-to-live of the cached result
     * @since 5.3.0
     */
    public void setHealthCheckTimeToLive(@Nullable Duration healthCheckTimeToLive) {
        this.healthCheckTimeToLive = healthCheckTimeToLive != null ? healthCheckTimeToLive : Duration.ZERO;
    }

    /**
     * The default connection pool configuration.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class SpringCloudConnectionPoolConfiguration extends ConnectionPoolConfiguration {
    }

    /**
     * Configuration class for Consul client config.
     */
    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class SpringConfigDiscoveryConfiguration extends ConfigDiscoveryConfiguration {

        /**
         * The full prefix for this configuration.
         */
        public static final String PREFIX = SpringCloudClientConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;

    }
}
