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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.net.InetAddress;
import java.util.Map;

/**
 * A catalog entry in Consul.
 * @see <a href="https://developer.hashicorp.com/consul/api-docs/catalog#sample-payload">Catalog Sample Payload</a>.
 * @author sdelamo
 * @since 4.1.0
 */
@Serdeable
public record ConsulCatalogEntry(@Nullable @JsonProperty("Node") String node,
                                 @Nullable @JsonProperty("Address") InetAddress address,
                                 @Nullable @JsonProperty("Datacenter") String datacenter,
                                 @Nullable @JsonProperty("TaggedAddresses") Map<String, String> taggedAddresses,
                                 @Nullable @JsonProperty("NodeMeta") Map<String, String> nodeMetadata,
                                 @Nullable @JsonProperty("Service") ConsulNewServiceEntry service) {
}
