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
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.serde.annotation.Serdeable;

import java.net.InetAddress;
import java.util.Map;

/**
 * A member entry of a Consul cluster. See https://www.consul.io/api/agent.html
 * @author Álvaro Sánchez-Mariscal
 */
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Serdeable
@ReflectiveAccess
public class MemberEntry {

    private String name;
    private String hostString;
    private Integer port;
    private Map<String, String> tags;
    private Integer status;

    /**
     * @return The name of this memeber
     */
    public String getName() {
        return name;
    }

    /**
     * @param name Name of this member
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return The address of this member as sent by Consul, a host name or an IP literal. It is not resolved.
     * @since 5.2.0
     */
    @JsonProperty("Addr")
    public String getHostString() {
        return hostString;
    }

    /**
     * @param hostString The address of this member, a host name or an IP literal
     * @since 5.2.0
     */
    @JsonProperty("Addr")
    public void setHostString(String hostString) {
        this.hostString = hostString;
    }

    /**
     * The address is resolved on each call, which needs a DNS lookup when Consul sent a host name.
     *
     * @return The resolved {@link InetAddress} of this member, or {@code null} if there is none
     * @throws java.io.UncheckedIOException if the host name cannot be resolved
     * @deprecated Use {@link #getHostString()} and resolve the address where it is needed.
     */
    @Deprecated(since = "5.2.0", forRemoval = true)
    @JsonIgnore
    public InetAddress getAddress() {
        return ConsulAddresses.resolve(hostString);
    }

    /**
     * Sets the address, stored as its host name, or as its IP literal when it has no host name.
     *
     * @param address The {@link InetAddress} of this member
     */
    @JsonIgnore
    public void setAddress(InetAddress address) {
        this.hostString = ConsulAddresses.toHostString(address);
    }

    /**
     * @return The port this member is listening on
     */
    public Integer getPort() {
        return port;
    }

    /**
     * @param port Listening port
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * @return Tags associated with this member
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * @param tags Tags associated with this member
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    /**
     * @return Status of this member
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * @param status Status of this member
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
}
