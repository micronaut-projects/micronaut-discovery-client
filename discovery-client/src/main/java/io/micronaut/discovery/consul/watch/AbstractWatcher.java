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

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.consul.client.v1.KeyValue;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueriesConsulClient;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * @param <V> The type of KeyValue to watch
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@Internal
abstract sealed class AbstractWatcher<V> implements Watcher permits ConfigurationsWatcher, NativeWatcher {

    protected static final Integer NO_INDEX = null;
    private static final Logger LOG = LoggerFactory.getLogger(AbstractWatcher.class);

    protected final BlockedQueriesConsulClient consulClient;
    protected final Map<String, V> kvHolder = new ConcurrentHashMap<>();

    private final List<String> kvPaths;
    private final BlockingQueriesConfiguration blockingQueriesConfiguration;
    private final PropertiesChangeHandler propertiesChangeHandler;
    private final WatchConfiguration watchConfiguration;

    private final Map<String, Disposable> listeners = new ConcurrentHashMap<>();
    private final Map<String, Long> retryCounts = new ConcurrentHashMap<>();

    private final Base64.Decoder base64Decoder = Base64.getDecoder();
    private volatile boolean started = false;
    private volatile boolean isInit = false;

    AbstractWatcher(final List<String> kvPaths,
                    final BlockedQueriesConsulClient consulClient,
                    final BlockingQueriesConfiguration blockingQueriesConfiguration,
                    final PropertiesChangeHandler propertiesChangeHandler,
                    final WatchConfiguration watchConfiguration) {
        this.kvPaths = kvPaths;
        this.consulClient = consulClient;
        this.blockingQueriesConfiguration = blockingQueriesConfiguration;
        this.propertiesChangeHandler = propertiesChangeHandler;
        this.watchConfiguration = watchConfiguration;
    }

    @Override
    public void start() {
        if (started) {
            throw new IllegalStateException("Watcher is already started");
        }

        try {
            LOG.debug("Starting KVs watcher");
            started = true;
            kvPaths.parallelStream()
                .forEach(kvPath -> watchKvPath(kvPath, Mono.delay(blockingQueriesConfiguration.getDelayDuration())));
        } catch (final Exception e) {
            LOG.error("Error watching configurations: {}", e.getMessage(), e);
            stop();
        }
    }

    @Override
    public boolean isWatching() {
        return started && isInit;
    }

    @Override
    public void stop() {
        if (!started) {
            LOG.warn("You tried to stop an unstarted Watcher");
            return;
        }

        LOG.debug("Stopping KVs watchers");
        listeners.forEach((key, value) -> {
            try {
                LOG.debug("Stopping watch for kvPath={}", key);
                value.dispose();
            } catch (final Exception e) {
                LOG.error("Error stopping configurations watcher for kvPath={}", key, e);
            }
        });
        listeners.clear();
        kvHolder.clear();
        started = false;
        isInit = false;
    }

    private void watchKvPath(final String kvPath, final Mono<Long> delayed) {
        if (!started) {
            LOG.warn("Watcher is not started");
            return;
        }

        // Ensure we clean up any previous listener
        final var previousListener = listeners.get(kvPath);
        if (previousListener != null && !previousListener.isDisposed()) {
            previousListener.dispose();
        }

        final var disposable = delayed
            .then(watchValue(kvPath))
            .subscribe(next -> onNext(kvPath, next), throwable -> onError(kvPath, throwable));

        listeners.put(kvPath, disposable);
    }

    protected abstract Mono<V> watchValue(String kvPath);

    private void onNext(final String kvPath, final V next) {
        // reset retry on success
        retryCounts.put(kvPath, 0L);

        final var previous = kvHolder.put(kvPath, next);

        if (previous == null) {
            handleInit(kvPath);
        } else if (areEqual(previous, next)) {
            handleNoChange(kvPath);
        } else {
            handleChange(kvPath, next, previous);
        }

        watchKvPath(kvPath, Mono.delay(blockingQueriesConfiguration.getDelayDuration()));
    }

    protected abstract boolean areEqual(V previous, V next);

    protected abstract Map<String, Object> readValue(V keyValue);

    private void onError(final String kvPath, final Throwable throwable) {
        if (throwable instanceof final HttpClientResponseException e && e.getStatus() == HttpStatus.NOT_FOUND) {
            LOG.atLevel(watchConfiguration.getKvNotFoundLogLevel()).log("No KV found with kvPath={}", kvPath);
            listeners.remove(kvPath);
        } else if (throwable instanceof ReadTimeoutException) {
            LOG.warn("Timeout for kvPath={}", kvPath);
            watchKvPath(kvPath, Mono.delay(blockingQueriesConfiguration.getDelayDuration()));
        } else {
            final var maxRetries = watchConfiguration.getMaxRetryAttempts();
            final var retry = this.retryCounts.merge(kvPath, 1L, Long::sum);
            if (retry <= maxRetries) {
                final var duration = calculateRetryDelay(retry);
                LOG.warn("Error detected, retrying watch ({}/{}) after delay={}ms", retry, maxRetries, duration.toMillis());
                // Add delay before retrying to avoid hammering the server
                watchKvPath(kvPath, Mono.delay(duration));
            } else {
                LOG.error("Max retry attempts {} reached, stopping watching path={}", maxRetries, kvPath, throwable);
                listeners.remove(kvPath);
            }
        }
    }

        /**
     * Calculates retry delay using exponential backoff with jitter.
     * <p>
     * This implementation prevents thundering herd problems by:
     * <ul>
     *   <li>Using exponential backoff (2^retryAttempt)</li>
     *   <li>Capping maximum delay at 30 seconds</li>
     *   <li>Adding random jitter (±25%) to spread out retry attempts</li>
     * </ul>
     *
     * @param retryAttempt the current retry attempt number (1-based)
     * @return the calculated delay duration
     */
    private Duration calculateRetryDelay(final long retryAttempt) {
        final var baseDelayMs = watchConfiguration.getRetryDelayMs();
        final var maxDelayMs = 30000; // 30 seconds cap

        // Exponential backoff: baseDelay * 2^(retryAttempt - 1)
        final long exponentialDelay = (long) (baseDelayMs * Math.pow(2, retryAttempt - 1.0));
        final long cappedDelay = Math.min(exponentialDelay, maxDelayMs);

        // Add jitter (±25%) to prevent thundering herd
        final long maxJitter = (long) (cappedDelay * 0.25);
        final long jitter = maxJitter > 0 ? ThreadLocalRandom.current().nextLong(-maxJitter, maxJitter + 1) : 0;

        return Duration.ofMillis(Math.max(0, cappedDelay + jitter));
    }

    private void handleInit(final String kvPath) {
        LOG.debug("Init watcher for kvPath={}", kvPath);
        this.isInit = true;
    }

    private void handleNoChange(final String kvPath) {
        LOG.debug("Nothing changed for kvPath={}", kvPath);
    }

    private void handleChange(final String kvPath, final V next, final V previous) {
        LOG.debug("Changes detected for kvPath={}", kvPath);
        final var previousValue = readValue(previous);
        final var nextValue = readValue(next);

        propertiesChangeHandler.handleChanges(kvPath, previousValue, nextValue);
    }

    protected final byte[] decodeValue(final KeyValue keyValue) {
        return base64Decoder.decode(keyValue.getValue());
    }

}
