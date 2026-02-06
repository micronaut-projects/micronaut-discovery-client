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
     * The default maxRetryAttempts value.
     */
    public static final Integer DEFAULT_MAX_RETRIES = 3;
    /**
     * The default retryDelayMs value.
     */
    public static final Integer DEFAULT_RETRY_DELAY = 500;
    /**
     * The default kvNotFoundLogLevel value.
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
     * @return Maximum number of retry attempts. Default to {@value DEFAULT_MAX_RETRIES}
     * @since 4.7.2
     */
    public @NonNull Integer getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    /**
     * @param maxRetryAttempts Maximum number of retry attempts.
     * @since 4.7.2
     */
    public void setMaxRetryAttempts(@NonNull final Integer maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    /**
     * @return Delay in milliseconds between retry attempts. Default to {@value DEFAULT_RETRY_DELAY}
     * @since 4.7.2
     */
    public @NonNull Integer getRetryDelayMs() {
        return retryDelayMs;
    }

    /**
     * @param retryDelayMs Delay in milliseconds between retry attempts
     * @since 4.7.2
     */
    public void setRetryDelayMs(@NonNull final Integer retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * @return Level to use to log NOT_FOUND KeyValue. Default to {@link Level#INFO}
     * @since 4.7.2
     */
    public @NonNull Level getKvNotFoundLogLevel() {
        return kvNotFoundLogLevel;
    }

    /**
     * @param kvNotFoundLogLevel Level to use to log NOT_FOUND KeyValue
     * @since 4.7.2
     */
    public void setKvNotFoundLogLevel(@NonNull final Level kvNotFoundLogLevel) {
        this.kvNotFoundLogLevel = kvNotFoundLogLevel;
    }
}
