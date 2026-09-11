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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.annotation.Serdeable;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Serdeable
@ReflectiveAccess
public class NodeEntry {

    private final String node;
    private final String hostString;
    private String datacenter;
    private Map<String, String> taggedAddresses;
    private Map<String, String> nodeMetadata;

    /**
     * Create a new catalog entry.
     *
     * @param nodeId     The node ID
     * @param hostString The node address as sent by Consul, a host name or an IP literal
     * @since 5.2.0
     */
    @JsonCreator
    public NodeEntry(@JsonProperty("Node") String nodeId, @JsonProperty("Address") String hostString) {
        this.node = nodeId;
        this.hostString = hostString;
    }

    /**
     * Create a new catalog entry. The address is stored as its host name, or as its IP literal when
     * it has no host name.
     *
     * @param nodeId  The node ID
     * @param address The node address
     * @deprecated Use {@link #NodeEntry(String, String)} instead.
     */
    @Deprecated(since = "5.2.0", forRemoval = true)
    public NodeEntry(String nodeId, InetAddress address) {
        this(nodeId, ConsulAddresses.toHostString(address));
    }

    /**
     * See https://www.consul.io/api/catalog.html#taggedaddresses.
     *
     * @return The tagged addresses
     */
    public Map<String, String> getTaggedAddresses() {
        if (taggedAddresses == null) {
            return Collections.emptyMap();
        }
        return taggedAddresses;
    }

    /**
     * See https://www.consul.io/api/catalog.html#taggedaddresses.
     *
     * @param taggedAddresses The tagged addresses
     */
    public void setTaggedAddresses(Map<String, String> taggedAddresses) {
        this.taggedAddresses = taggedAddresses;
    }

    /**
     * See https://www.consul.io/api/catalog.html#nodemeta.
     *
     * @return The node metadata
     */
    public Map<String, String> getNodeMetadata() {
        if (nodeMetadata == null) {
            return Collections.emptyMap();
        }
        return nodeMetadata;
    }

    /**
     * @param nodeMetadata The node metadata
     * @return The {@link NodeEntry} instance
     */
    public NodeEntry nodeMetadata(Map<String, String> nodeMetadata) {
        this.nodeMetadata = nodeMetadata;
        return this;
    }

    /**
     * See https://www.consul.io/api/catalog.html#nodemeta.
     *
     * @param nodeMetadata The node metadata
     */
    public void setNodeMetadata(Map<String, String> nodeMetadata) {
        this.nodeMetadata = nodeMetadata;
    }

    /**
     * See https://www.consul.io/api/catalog.html#node.
     *
     * @return The node ID
     */
    public String getNode() {
        return node;
    }

    /**
     * See https://www.consul.io/api/catalog.html#address.
     *
     * @return The node address as sent by Consul, a host name or an IP literal. It is not resolved.
     * @since 5.2.0
     */
    @JsonProperty("Address")
    public String getHostString() {
        return hostString;
    }

    /**
     * See https://www.consul.io/api/catalog.html#address.
     *
     * <p>The address is resolved on each call, which needs a DNS lookup when Consul sent a host name.</p>
     *
     * @return The resolved node address, or {@code null} if there is none
     * @throws java.io.UncheckedIOException if the host name cannot be resolved
     * @deprecated Use {@link #getHostString()} and resolve the address where it is needed.
     */
    @Deprecated(since = "5.2.0", forRemoval = true)
    @JsonIgnore
    public InetAddress getAddress() {
        return ConsulAddresses.resolve(hostString);
    }

    /**
     * See https://www.consul.io/api/catalog.html#datacenter.
     *
     * @return The data center to use
     */
    public Optional<String> getDatacenter() {
        return Optional.ofNullable(datacenter);
    }

    /**
     * See https://www.consul.io/api/catalog.html#datacenter.
     *
     * @param datacenter The data center to use
     */
    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    /**
     * @param datacenter The datacenter
     * @return The {@link NodeEntry} instance
     */
    public NodeEntry datacenter(String datacenter) {
        if (StringUtils.isNotEmpty(datacenter)) {
            this.datacenter = datacenter;
        }
        return this;
    }

    /**
     * @param taggedAddresses The tagged addresses
     * @return The {@link NodeEntry} instance
     */
    public NodeEntry taggedAddresses(Map<String, String> taggedAddresses) {
        this.taggedAddresses = taggedAddresses;
        return this;
    }
}
