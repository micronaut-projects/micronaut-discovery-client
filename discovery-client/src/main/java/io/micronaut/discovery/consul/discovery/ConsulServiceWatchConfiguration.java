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
package io.micronaut.discovery.consul.discovery;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.http.client.HttpClientConfiguration;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration of the watch that keeps the instances of discovered Consul services up to date with
 * <a href="https://developer.hashicorp.com/consul/api-docs/features/blocking">blocking queries</a>.
 *
 * <p>The HTTP client settings are copied from {@link ConsulConfiguration}. The read timeout is derived from
 * {@link #getWait()} so that blocking queries are not cut short.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@RequiresConsul
@Requires(property = ConsulServiceWatchConfiguration.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@ConfigurationProperties(ConsulServiceWatchConfiguration.PREFIX)
public class ConsulServiceWatchConfiguration extends HttpClientConfiguration implements Toggleable {

    /**
     * The prefix of the service watch settings.
     */
    public static final String PREFIX = ConsulConfiguration.PREFIX + ".discovery.watch";

    /**
     * The default blocking query wait.
     */
    public static final Duration DEFAULT_WAIT = Duration.ofSeconds(55);

    /**
     * The default delay between two blocking queries of a service.
     */
    public static final Duration DEFAULT_DELAY = Duration.ofMillis(50);

    /**
     * The default delay before retrying a failed query.
     */
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

    /**
     * The default maximum delay before retrying a failed query.
     */
    public static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final ConsulConfiguration consulConfiguration;
    private boolean enabled;
    private Duration wait = DEFAULT_WAIT;
    private Duration delay = DEFAULT_DELAY;
    private Duration retryDelay = DEFAULT_RETRY_DELAY;
    private Duration maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;
    private Duration idleTimeout;

    /**
     * @param consulConfiguration The Consul configuration whose HTTP client settings are copied
     */
    public ConsulServiceWatchConfiguration(ConsulConfiguration consulConfiguration) {
        super(consulConfiguration);
        this.consulConfiguration = consulConfiguration;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether the instances of discovered services are watched and cached. Default value {@code false}.
     *
     * @param enabled True to watch service instances
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The maximum time a blocking query waits for a change
     */
    public Duration getWait() {
        return wait;
    }

    /**
     * The maximum time a blocking query waits for a change, sent as the {@code wait} parameter. Consul caps it at
     * 10 minutes. Default value 55 seconds.
     *
     * @param wait The blocking query wait
     */
    public void setWait(Duration wait) {
        this.wait = wait != null && !wait.isNegative() && !wait.isZero() ? wait : DEFAULT_WAIT;
    }

    /**
     * @return The delay between two blocking queries of a service
     */
    public Duration getDelay() {
        return delay;
    }

    /**
     * The delay between two blocking queries of a service, to avoid flooding Consul when its index changes often.
     * Default value 50 milliseconds.
     *
     * @param delay The delay
     */
    public void setDelay(Duration delay) {
        this.delay = delay != null && !delay.isNegative() ? delay : DEFAULT_DELAY;
    }

    /**
     * @return The delay before retrying a failed query
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * The delay before retrying a failed query. It doubles after each consecutive failure up to
     * {@link #getMaxRetryDelay()}. Default value 1 second.
     *
     * @param retryDelay The retry delay
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay != null && !retryDelay.isNegative() ? retryDelay : DEFAULT_RETRY_DELAY;
    }

    /**
     * @return The maximum delay before retrying a failed query
     */
    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * The maximum delay before retrying a failed query. Default value 30 seconds.
     *
     * @param maxRetryDelay The maximum retry delay
     */
    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay != null && !maxRetryDelay.isNegative() ? maxRetryDelay : DEFAULT_MAX_RETRY_DELAY;
    }

    /**
     * @return The time after which a service that has not been looked up stops being watched, if eviction is enabled
     */
    public Optional<Duration> getIdleTimeout() {
        return Optional.ofNullable(idleTimeout);
    }

    /**
     * The time after which a service that has not been looked up stops being watched. The next lookup of the service
     * starts a new watch. A zero, negative or absent value disables eviction, which is the default.
     *
     * @param idleTimeout The idle timeout
     */
    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout != null && !idleTimeout.isNegative() && !idleTimeout.isZero() ? idleTimeout : null;
    }

    /**
     * @return A read timeout longer than {@link #getWait()} plus the jitter of up to a sixteenth that Consul adds
     */
    @Override
    public Optional<Duration> getReadTimeout() {
        return Optional.of(wait.plus(wait.dividedBy(16)).plusSeconds(5));
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return consulConfiguration.getConnectionPoolConfiguration();
    }
}
