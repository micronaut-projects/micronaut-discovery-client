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
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Base class for a service entry in consul.
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Introspected
@ReflectiveAccess
public abstract class AbstractServiceEntry {

    protected final String name;
    private String hostString;
    private Integer port;
    private List<String> tags;
    private String ID;
    private Map<String, String> meta;

    /**
     * @param name The service name
     */
    public AbstractServiceEntry(String name) {
        this.name = name;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#id.
     *
     * @return The ID of the service
     */
    @JsonProperty("ID")
    public Optional<String> getID() {
        return Optional.ofNullable(ID);
    }

    /**
     * See https://www.consul.io/api/agent/service.html#id.
     *
     * @param id The ID of the service
     */
    @JsonProperty("ID")
    public void setID(String id) {
        this.ID = id;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address.
     *
     * @return The address of the service, a host name or an IP literal. It is not resolved.
     * @since 5.2.0
     */
    @JsonProperty("Address")
    public Optional<String> getHostString() {
        return Optional.ofNullable(hostString);
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address.
     *
     * @param hostString The address of the service, a host name or an IP literal
     * @since 5.2.0
     */
    @JsonProperty("Address")
    public void setHostString(String hostString) {
        this.hostString = hostString;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address.
     *
     * <p>The address is resolved on each call, which needs a DNS lookup when it is a host name.</p>
     *
     * @return The resolved address of the service
     * @throws java.io.UncheckedIOException if the host name cannot be resolved
     * @deprecated Use {@link #getHostString()} and resolve the address where it is needed.
     */
    @Deprecated(since = "5.2.0", forRemoval = true)
    @JsonIgnore
    public Optional<InetAddress> getAddress() {
        return Optional.ofNullable(ConsulAddresses.resolve(hostString));
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address.
     *
     * <p>The address is stored as its host name, or as its IP literal when it has no host name.</p>
     *
     * @param address The address of the service
     */
    @JsonIgnore
    public void setAddress(InetAddress address) {
        this.hostString = ConsulAddresses.toHostString(address);
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address.
     *
     * @return The port of the service
     */
    public OptionalInt getPort() {
        if (port != null) {
            return OptionalInt.of(port);
        } else {
            return OptionalInt.empty();
        }
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address.
     *
     * @param port The port of the service
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#tags.
     *
     * @return The service tags
     */
    public List<String> getTags() {
        if (tags == null) {
            return Collections.emptyList();
        }
        return tags;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#tags.
     *
     * @param tags The service tags
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#meta.
     *
     * @return The service metadata
     */
    public Map<String, String> getMeta() {
        if (meta == null) {
            return Collections.emptyMap();
        }
        return meta;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#meta.
     *
     * @param meta The service metadata
     */
    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#name.
     *
     * @return The name of the service
     */
    public String getName() {
        return name;
    }

    /**
     * @param id The id of the service
     * @return The {@link AbstractServiceEntry} instance
     */
    public AbstractServiceEntry id(String id) {
        this.ID = id;
        return this;
    }

    /**
     * @param address The {@link InetAddress } of the service, stored as its host name, or as its IP
     *                literal when it has no host name
     * @return The {@link AbstractServiceEntry} instance
     */
    public AbstractServiceEntry address(InetAddress address) {
        this.hostString = ConsulAddresses.toHostString(address);
        return this;
    }

    /**
     * Sets the address as given. Since 5.2.0 it is no longer resolved, so a host name is sent to
     * Consul unchanged.
     *
     * @param address The address of the service, a host name or an IP literal
     * @return The {@link AbstractServiceEntry} instance
     */
    public AbstractServiceEntry address(String address) {
        this.hostString = address;
        return this;
    }

    /**
     * @param port The port of the service
     * @return The {@link AbstractServiceEntry} instance
     */
    public AbstractServiceEntry port(Integer port) {
        this.port = port;
        return this;
    }

    /**
     * @param tags The service tags
     * @return The {@link AbstractServiceEntry} instance
     */
    public AbstractServiceEntry tags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    /**
     *
     * @param meta The service metadata
     * @return The {@link AbstractServiceEntry} instance
     */
    public AbstractServiceEntry meta(Map<String, String> meta) {
        this.meta = meta;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractServiceEntry that = (AbstractServiceEntry) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(hostString, that.hostString) &&
            Objects.equals(port, that.port) &&
            Objects.equals(tags, that.tags) &&
            Objects.equals(meta, that.meta) &&
            Objects.equals(ID, that.ID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, hostString, port, tags, meta, ID);
    }
}
