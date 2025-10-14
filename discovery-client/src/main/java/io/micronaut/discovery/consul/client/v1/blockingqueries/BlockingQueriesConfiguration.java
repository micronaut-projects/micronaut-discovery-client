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
package io.micronaut.discovery.consul.client.v1.blockingqueries;

import java.time.Duration;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.http.client.HttpClientConfiguration;

/**
 * Configuration for <a href="https://developer.hashicorp.com/consul/api-docs/features/blocking">Consul Blocking Queries</a>.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@RequiresConsul
@ConfigurationProperties(BlockingQueriesConfiguration.PREFIX)
public class BlockingQueriesConfiguration extends HttpClientConfiguration {

    /**
     * The prefix to use for all Consul's blocked queries settings.
     */
    public static final String PREFIX = ConsulConfiguration.PREFIX + ".blocking-queries";

    /**
     * The default max wait duration in minutes.
     */
    public static final String DEFAULT_MAX_WAIT_DURATION_MINUTES = "10m";

    /**
     * The default delay duration in milliseconds.
     */
    public static final long DEFAULT_DELAY_DURATION_MILLISECONDS = 50;

    private String maxWaitDuration = DEFAULT_MAX_WAIT_DURATION_MINUTES;
    private Duration delayDuration = Duration.ofMillis(DEFAULT_DELAY_DURATION_MILLISECONDS);
    private Duration readTimeout = null;

    private final ConsulConfiguration consulConfiguration;
    private final ConversionService conversionService;

    /**
     * Default constructor.
     *
     * @param consulConfiguration {@link ConsulConfiguration} used as base.
     * @param conversionService   Use to calculate the {@link #readTimeout} from the {@link #maxWaitDuration}.
     * @see ConsulConfiguration
     */
    public BlockingQueriesConfiguration(final ConsulConfiguration consulConfiguration,
                                        final ConversionService conversionService) {
        super(consulConfiguration);
        this.consulConfiguration = consulConfiguration;
        this.conversionService = conversionService;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return consulConfiguration.getConnectionPoolConfiguration();
    }

    /**
     * @return The read timeout, depending on the {@link #maxWaitDuration} value.
     */
    @Override
    public Optional<Duration> getReadTimeout() {
        if (this.readTimeout == null) {
            this.readTimeout = calculateReadTimeout();
        }
        return Optional.of(this.readTimeout);
    }

    private Duration calculateReadTimeout() {
        final var waitValue = Optional.ofNullable(getMaxWaitDuration())
            .orElse(DEFAULT_MAX_WAIT_DURATION_MINUTES);

        final var duration = conversionService.convertRequired(waitValue, Duration.class);
        // to have the client timeout greater than the wait of the Blocked Query
        return duration.plusMillis(duration.toMillis() / 16);
    }

    /**
     * @return The max wait duration. Defaults to {@value DEFAULT_MAX_WAIT_DURATION_MINUTES}.
     */
    @Nullable
    public String getMaxWaitDuration() {
        return this.maxWaitDuration;
    }

    /**
     * Specify the maximum duration for the blocking request. Default value ({@value #DEFAULT_MAX_WAIT_DURATION_MINUTES}).
     *
     * @param maxWaitDuration The wait timeout
     */
    public void setMaxWaitDuration(@Nullable final String maxWaitDuration) {
        this.maxWaitDuration = maxWaitDuration;
        this.readTimeout = calculateReadTimeout();
    }

    /**
     * @return The delay duration. Defaults to {@value DEFAULT_DELAY_DURATION_MILLISECONDS} milliseconds.
     */
    @NonNull
    public Duration getDelayDuration() {
        return this.delayDuration;
    }

    /**
     * Sets the delay before each call to avoid flooding. Default value ({@value #DEFAULT_DELAY_DURATION_MILLISECONDS} milliseconds).
     *
     * @param delayDuration The watch delay
     */
    public void setDelayDuration(final Duration delayDuration) {
        if (delayDuration == null) {
            this.delayDuration = Duration.ofMillis(DEFAULT_DELAY_DURATION_MILLISECONDS);
        } else {
            this.delayDuration = delayDuration;
        }
    }
}
