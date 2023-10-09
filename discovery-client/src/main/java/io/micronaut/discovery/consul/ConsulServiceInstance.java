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
package io.micronaut.discovery.consul;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.client.v1.*;
import io.micronaut.discovery.exceptions.DiscoveryException;
import io.micronaut.health.HealthStatus;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link ServiceInstance} for Consul.
 *
 * @author graemerocher
 * @since 1.0
 */
public class ConsulServiceInstance implements ServiceInstance {

    private final ConsulHealthEntry healthEntry;
    private final URI uri;
    private ConvertibleValues<String> metadata;

    /**
     * Constructs a {@link ConsulServiceInstance} for the given {@link ConsulHealthEntry} and scheme.
     *
     * @param healthEntry The health entry
     * @param scheme      The scheme
     */
    public ConsulServiceInstance(@NonNull ConsulHealthEntry healthEntry, @Nullable String scheme) {
        Objects.requireNonNull(healthEntry, "ConsulHealthEntry cannot be null");
        this.healthEntry = healthEntry;
        ConsulServiceEntry service = healthEntry.service();
        Objects.requireNonNull(service, "ConsulHealthEntry cannot reference a null service entry");
        NodeEntry node = healthEntry.node();
        Objects.requireNonNull(service, "ConsulHealthEntry cannot reference a null node entry");

        String inetAddress = service.address() != null ? service.address() : node.getAddress().getHostAddress();
        int port = service.port() != null ? service.port() : -1;
        String portSuffix = port > -1 ? ":" + port : "";
        String uriStr = (scheme != null ? scheme + "://" : "http://") + inetAddress + portSuffix;
        try {
            this.uri = new URI(uriStr);
        } catch (URISyntaxException e) {
            throw new DiscoveryException("Invalid service URI: " + uriStr);
        }
    }

    /**
     * Constructs a {@link ConsulServiceInstance} for the given {@link ConsulHealthEntry} and scheme.
     *
     * @param healthEntry The health entry
     * @param scheme      The scheme
     * @deprecated use {@link ConsulServiceInstance(ConsulHealthEntry, String)} instead.
     */
    @Deprecated
    public ConsulServiceInstance(@NonNull HealthEntry healthEntry, @Nullable String scheme) {
        this.healthEntry = null;
        this.uri = null;
    }

    @Override
    public HealthStatus getHealthStatus() {
        List<ConsulCheck> checks = healthEntry.checks();
        if (CollectionUtils.isNotEmpty(checks)) {
            Stream<ConsulCheck> criticalStream = checks.stream().filter(c -> c.getStatus().equals(ConsulCheckStatus.CRITICAL.toString()));
            Optional<ConsulCheck> first = criticalStream.findFirst();
            if (first.isPresent()) {
                ConsulCheck check = first.get();
                String notes = check.getNotes();
                if (StringUtils.isNotEmpty(notes)) {
                    return HealthStatus.DOWN.describe(notes);
                } else {
                    return HealthStatus.DOWN;
                }
            }
        }
        return HealthStatus.UP;
    }

    /**
     * @return The {@link ConsulHealthEntry}
     * @deprecated not used
     */
    @Deprecated(forRemoval = true, since = "4.1.0")
    public HealthEntry getHealthEntry() {
        return null;
    }

    @Override
    public String getId() {
        return healthEntry.service().id();
    }

    @Override
    public Optional<String> getInstanceId() {
        return Optional.ofNullable(healthEntry.service().id());
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        ConvertibleValues<String> metadata = this.metadata;
        if (metadata == null) {
            synchronized (this) { // double check
                metadata = this.metadata;
                if (metadata == null) {
                    metadata = buildMetadata();
                    this.metadata = metadata;
                }
            }
        }
        return metadata;
    }

    private ConvertibleValues<String> buildMetadata() {
        Map<CharSequence, String> map = new LinkedHashMap<>(healthEntry.node().getNodeMetadata());
        List<String> tags = healthEntry.service().tags();
        if (tags != null) {
            for (String tag : tags) {
                int i = tag.indexOf('=');
                if (i > -1) {
                    map.put(tag.substring(0, i), tag.substring(i + 1));
                }
            }
        }

        Map<String, String> meta = healthEntry.service().meta();
        if (meta != null) {
            map.putAll(meta);
        }
        return ConvertibleValues.of(map);
    }
}
