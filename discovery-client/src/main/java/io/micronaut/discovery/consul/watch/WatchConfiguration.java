/*
 * Copyright 2017-2025 original authors
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

import org.slf4j.event.Level;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.consul.ConsulConfiguration;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for Consul {@link Watcher}.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@ConfigurationProperties(WatchConfiguration.PREFIX)
public class WatchConfiguration implements Toggleable {

    /**
     * The prefix to use for Consul's watcher settings.
     */
    public static final String PREFIX = ConsulConfiguration.PREFIX + ".watch";

    /**
     * The default enable value.
     */
    public static final boolean DEFAULT_ENABLED = false;
    /**
     * The default maxRetryAttempts value. A negative value means that failed watches are retried indefinitely.
     *
     * @since 5.3.0
     */
    public static final Integer DEFAULT_MAX_RETRIES = -1;
    /**
     * The default retryDelayMs value.
     *
     * @since 5.3.0
     */
    public static final Integer DEFAULT_RETRY_DELAY = 500;
    /**
     * The default maxRetryDelay value.
     *
     * @since 5.3.0
     */
    public static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofSeconds(30);
    /**
     * The default kvNotFoundLogLevel value.
     *
     * @since 5.3.0
     */
    public static final Level DEFAULT_KV_NOT_FOUND_LOG_LEVEL = Level.INFO;

    /**
     * Internal property used to carry importer-derived watch paths into the Consul watcher.
     *
     * @since 5.0.0
     */
    public static final String IMPORTED_PATHS = PREFIX + ".imported-paths";

    /**
     * Internal property used to carry the importer-derived Consul config format into the watcher.
     *
     * @since 5.0.0
     */
    public static final String IMPORTED_FORMAT = PREFIX + ".imported-format";

    private boolean enabled = DEFAULT_ENABLED;
    private String importedPaths;
    private String importedFormat;

    private Integer maxRetryAttempts = DEFAULT_MAX_RETRIES;

    private Integer retryDelayMs = DEFAULT_RETRY_DELAY;

    private Duration maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;

    private Level kvNotFoundLogLevel = DEFAULT_KV_NOT_FOUND_LOG_LEVEL;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether Configuration watching is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if it is enabled
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The explicit Consul KV paths supplied by config import metadata, if any.
     * @since 5.0.0
     */
    public Optional<List<String>> getImportedPaths() {
        return Optional.ofNullable(importedPaths)
            .map(paths -> Arrays.stream(paths.split(","))
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .toList());
    }

    /**
     * Sets the explicit Consul KV paths supplied by importer metadata.
     *
     * @param importedPaths A comma-separated list of explicit Consul KV watch paths
     * @since 5.0.0
     */
    public void setImportedPaths(String importedPaths) {
        this.importedPaths = importedPaths;
    }

    /**
     * @return The importer-derived Consul configuration format, if any.
     * @since 5.0.0
     */
    public Optional<String> getImportedFormat() {
        return Optional.ofNullable(importedFormat);
    }

    /**
     * Sets the Consul configuration format supplied by importer metadata.
     *
     * @param importedFormat The importer-derived Consul configuration format
     * @since 5.0.0
     */
    public void setImportedFormat(String importedFormat) {
        this.importedFormat = importedFormat;
    }

    /**
     * @return The maximum number of consecutive times a failed watch of a KV path is retried before the
     * watch of that path is stopped. A negative value means unlimited retries. Defaults to {@code -1}.
     * @since 5.3.0
     */
    public @NonNull Integer getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    /**
     * Sets the maximum number of consecutive times a failed watch of a KV path is retried before the watch of
     * that path is stopped. A negative value (the default, {@code -1}) retries indefinitely, so the watcher
     * recovers once Consul is reachable again. The retry counter is reset each time the watch succeeds. Read
     * timeouts and missing KV paths do not count as failures. Micronaut Discovery Client 4.7.2 introduced this
     * setting with a default of {@code 3}.
     *
     * @param maxRetryAttempts The maximum number of retry attempts, or a negative value for unlimited retries
     * @since 5.3.0
     */
    public void setMaxRetryAttempts(@NonNull final Integer maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    /**
     * @return The base delay in milliseconds before retrying a failed watch. Defaults to {@code 500}.
     * @since 5.3.0
     */
    public @NonNull Integer getRetryDelayMs() {
        return retryDelayMs;
    }

    /**
     * Sets the base delay in milliseconds before retrying a failed watch. The delay grows exponentially with each
     * consecutive failure ({@code retryDelayMs * 2^(attempt - 1)}), is capped at {@link #getMaxRetryDelay()} and
     * has a random jitter of up to 25% applied. Defaults to {@code 500}.
     *
     * @param retryDelayMs The base retry delay in milliseconds
     * @since 5.3.0
     */
    public void setRetryDelayMs(@NonNull final Integer retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * @return The maximum delay between two retries of a failed watch. Defaults to 30 seconds.
     * @since 5.3.0
     */
    public @NonNull Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * Sets the maximum delay between two retries of a failed watch. The exponential backoff computed from
     * {@link #getRetryDelayMs()} is capped at this value. It is also the interval at which a KV path that does
     * not exist in Consul is polled until it is created. Defaults to 30 seconds.
     *
     * @param maxRetryDelay The maximum retry delay
     * @since 5.3.0
     */
    public void setMaxRetryDelay(@NonNull final Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    /**
     * @return The log level used when a watched KV path does not exist in Consul. Defaults to {@link Level#INFO}.
     * @since 5.3.0
     */
    public @NonNull Level getKvNotFoundLogLevel() {
        return kvNotFoundLogLevel;
    }

    /**
     * Sets the log level used when a watched KV path does not exist in Consul (HTTP 404). The message is logged
     * once each time the path goes missing, and the path is checked again every {@link #getMaxRetryDelay()}
     * until it exists. Use {@code WARN} or {@code ERROR} to make a misconfigured path visible. Valid
     * values are {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN} and {@code ERROR}. Defaults to
     * {@link Level#INFO}.
     *
     * @param kvNotFoundLogLevel The log level
     * @since 5.3.0
     */
    public void setKvNotFoundLogLevel(@NonNull final Level kvNotFoundLogLevel) {
        this.kvNotFoundLogLevel = kvNotFoundLogLevel;
    }
}
