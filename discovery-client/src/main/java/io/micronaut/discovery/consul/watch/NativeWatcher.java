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

import static io.micronaut.discovery.config.ConfigDiscoveryConfiguration.Format;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.client.v1.KeyValue;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueriesConsulClient;
import reactor.core.publisher.Mono;

/**
 * Watcher handling {@link Format#NATIVE} configurations.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
final class NativeWatcher extends AbstractWatcher<List<KeyValue>> {

    private static final Logger LOG = LoggerFactory.getLogger(NativeWatcher.class);

    private final Map<String, String> keysMap = new HashMap<>();

    /**
     * Default constructor.
     */
    NativeWatcher(final List<String> kvPaths,
                         final BlockedQueriesConsulClient consulClient,
                         final BlockingQueriesConfiguration blockingQueriesConfiguration,
                         final PropertiesChangeHandler propertiesChangeHandler) {
        super(kvPaths, consulClient, blockingQueriesConfiguration, propertiesChangeHandler);
    }

    @Override
    protected Mono<List<KeyValue>> watchValue(final String kvPath) {
        final var modifiedIndex = Optional.ofNullable(kvHolder.get(kvPath))
                .stream()
                .flatMap(List::stream)
                .map(KeyValue::getModifyIndex)
                .max(Integer::compareTo)
                .orElse(NO_INDEX);
        LOG.debug("Watching kvPath={} with index={}", kvPath, modifiedIndex);
        return consulClient.watchValues(kvPath, true, modifiedIndex);
    }

    @Override
    protected boolean areEqual(final List<KeyValue> previous, final List<KeyValue> next) {
        return KvUtils.areEqual(previous, next);
    }

    @Override
    protected Map<String, Object> readValue(final List<KeyValue> keyValues) {
        if (keyValues == null) {
            return Collections.emptyMap();
        }

        return keyValues.stream()
                .filter(Objects::nonNull)
                .filter(kv -> StringUtils.isNotEmpty(kv.getValue()))
                .collect(Collectors.toMap(this::pathToPropertyKey, keyValue -> new String(decodeValue(keyValue))));
    }

    private String pathToPropertyKey(final KeyValue kv) {
        return keysMap.computeIfAbsent(kv.getKey(), key -> CollectionUtils.last(List.of(key.split("/"))));
    }

}
