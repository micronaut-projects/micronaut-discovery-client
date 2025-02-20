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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;

/**
 * Handle properties' configuration changes to be notified into the Micronaut context.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@Internal
@Singleton
public class PropertiesChangeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PropertiesChangeHandler.class);

    private final Environment environment;
    private final ApplicationEventPublisher<RefreshEvent> eventPublisher;

    private final Map<String, String> propertySourceNames = new ConcurrentHashMap<>();

    PropertiesChangeHandler(final Environment environment,
                            final ApplicationEventPublisher<RefreshEvent> eventPublisher) {
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Update Micronaut context with properties changes, then notify them.
     *
     * @param kvPath   KV path corresponding to the PropertySource updated
     * @param previous Previous values of the PropertySource
     * @param next     New values of the PropertySource
     */
    void handleChanges(final String kvPath,
                       final Map<String, Object> previous,
                       final Map<String, Object> next) {
        try {
            final var copyNext = new LinkedHashMap<>(next);
            final var differences = new HashMap<String, Object>();

            previous.forEach((key, previousValue) -> {
                if (next.containsKey(key)) {
                    final var nextValue = copyNext.remove(key);
                    if (!Objects.deepEquals(previousValue, nextValue)) {
                        // updated properties
                        differences.put(key, previousValue);
                    }
                } else {
                    // deleted properties
                    differences.put(key, previousValue);
                }
            });

            // remaining = added properties -> consider previous values as null
            copyNext.keySet().forEach(key -> differences.put(key, null));

            if (differences.isEmpty()) {
                LOG.debug("No properties differences found for update of kvPath={}", kvPath);
            } else {
                updatePropertySources(kvPath, next);

                publishDifferences(differences);
            }
        } catch (final Exception e) {
            LOG.error("Unable to apply configuration changes", e);
        }
    }

    private void updatePropertySources(final String kvPath, final Map<String, Object> newValues) {
        final var propertySourceName = toPropertySourceName(kvPath);
        LOG.debug("Updating context with new configurations for {}", propertySourceName);

        final var updatedPropertySources = new ArrayList<PropertySource>();
        for (final var propertySource : environment.getPropertySources()) {
            if (propertySource.getName().equals(propertySourceName)) {
                // creating a new PropertySource with new values but keeping the order
                updatedPropertySources.add(PropertySource.of(propertySourceName, newValues, propertySource.getOrder()));
            } else {
                updatedPropertySources.add(propertySource);
            }
        }

        updatedPropertySources.stream()
            // /!\ re-setting all the propertySources sorted by Order, to keep precedence
            .sorted(Comparator.comparing(PropertySource::getOrder))
            .forEach(environment::addPropertySource);
    }

    private String toPropertySourceName(final String kvPath) {
        return propertySourceNames.computeIfAbsent(kvPath, PropertiesChangeHandler::resolvePropertySourceName);
    }

    private static String resolvePropertySourceName(final String kvPath) {
        final var propertySourceName = CollectionUtils.last(List.of(kvPath.split("/")));
        final var tokens = propertySourceName.split(",");
        if (tokens.length == 1) {
            return ConsulClient.SERVICE_ID + '-' + propertySourceName;
        }

        final var name = tokens[0];
        final var envName = tokens[1];

        return ConsulClient.SERVICE_ID + '-' + name + '[' + envName + ']';
    }

    private void publishDifferences(final Map<String, Object> changes) {
        if (changes.isEmpty()) {
            return;
        }

        LOG.debug("Configuration has been updated, publishing RefreshEvent.");
        eventPublisher.publishEvent(new RefreshEvent(changes));
    }
}
