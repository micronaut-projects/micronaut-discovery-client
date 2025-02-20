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

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<String, Disposable> listeners = new ConcurrentHashMap<>();

    private final Base64.Decoder base64Decoder = Base64.getDecoder();
    private volatile boolean started = false;
    private volatile boolean isInit = false;

    AbstractWatcher(final List<String> kvPaths,
                    final BlockedQueriesConsulClient consulClient,
                    final BlockingQueriesConfiguration blockingQueriesConfiguration,
                    final PropertiesChangeHandler propertiesChangeHandler) {
        this.kvPaths = kvPaths;
        this.consulClient = consulClient;
        this.blockingQueriesConfiguration = blockingQueriesConfiguration;
        this.propertiesChangeHandler = propertiesChangeHandler;
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
                .forEach(this::watchKvPath);
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

    private void watchKvPath(final String kvPath) {
        if (!started) {
            LOG.warn("Watcher is not started");
            return;
        }
        // delaying to avoid flood caused by multiple consecutive calls
        final var disposable = Mono.delay(blockingQueriesConfiguration.getDelayDuration())
            .then(watchValue(kvPath))
            .subscribe(next -> onNext(kvPath, next), throwable -> onError(kvPath, throwable));

        listeners.put(kvPath, disposable);
    }

    protected abstract Mono<V> watchValue(String kvPath);

    private void onNext(final String kvPath, final V next) {
        final var previous = kvHolder.put(kvPath, next);

        if (previous == null) {
            handleInit(kvPath);
        } else if (areEqual(previous, next)) {
            handleNoChange(kvPath);
        } else {
            handleChange(kvPath, next, previous);
        }

        watchKvPath(kvPath);
    }

    protected abstract boolean areEqual(V previous, V next);

    protected abstract Map<String, Object> readValue(V keyValue);

    private void onError(final String kvPath, final Throwable throwable) {
        if (throwable instanceof final HttpClientResponseException e && e.getStatus() == HttpStatus.NOT_FOUND) {
            LOG.trace("No KV found with kvPath={}", kvPath);
            listeners.remove(kvPath);
        } else if (throwable instanceof ReadTimeoutException) {
            LOG.warn("Timeout for kvPath={}", kvPath);
            watchKvPath(kvPath);
        } else {
            LOG.error("Watching kvPath={} failed", kvPath, throwable);
            listeners.remove(kvPath);
        }
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
