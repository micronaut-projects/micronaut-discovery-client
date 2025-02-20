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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.env.PropertySourceReader;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.client.v1.KeyValue;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueriesConsulClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Watcher handling {@link Format#JSON}, {@link Format#PROPERTIES} and {@link Format#YAML} configurations.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
final class ConfigurationsWatcher extends AbstractWatcher<KeyValue> {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationsWatcher.class);

    private final PropertySourceReader propertySourceReader;

    /**
     * Default constructor.
     */
    ConfigurationsWatcher(final List<String> kvPaths,
                                 final BlockedQueriesConsulClient consulClient,
                                 final BlockingQueriesConfiguration blockingQueriesConfiguration,
                                 final PropertiesChangeHandler propertiesChangeHandler,
                                 final PropertySourceReader propertySourceReader) {
        super(kvPaths, consulClient, blockingQueriesConfiguration, propertiesChangeHandler);
        this.propertySourceReader = propertySourceReader;
    }

    @Override
    protected Mono<KeyValue> watchValue(final String kvPath) {
        final var modifiedIndex = Optional.ofNullable(kvHolder.get(kvPath))
                .map(KeyValue::getModifyIndex)
                .orElse(NO_INDEX);
        LOG.debug("Watching kvPath={} with index={}", kvPath, modifiedIndex);
        return consulClient.watchValues(kvPath, false, modifiedIndex)
                .flatMapMany(Flux::fromIterable)
                .filter(kv -> kvPath.equals(kv.getKey()))
                .singleOrEmpty();
    }

    @Override
    protected boolean areEqual(final KeyValue previous, final KeyValue next) {
        return KvUtils.areEqual(previous, next);
    }

    @Override
    protected Map<String, Object> readValue(final KeyValue keyValue) {
        if (keyValue == null || StringUtils.isEmpty(keyValue.getValue())) {
            return Collections.emptyMap();
        }

        return propertySourceReader.read(keyValue.getKey(), decodeValue(keyValue));
    }

}
