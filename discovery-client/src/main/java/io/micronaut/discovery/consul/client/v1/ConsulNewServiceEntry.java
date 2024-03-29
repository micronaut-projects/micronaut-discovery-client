/*
 * Copyright 2017-2023 original authors
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

import java.util.List;
import java.util.Map;

/**
 * <a href="https://developer.hashicorp.com/consul/api-docs/agent/service#json-request-body-schema">Register Service - JSON Request Body Schema</a>.
 *
 * @param name The logical name of hte service
 * @param address The address of the service
 * @param port The port of the service
 * @param tags A list of tags to assign to the service
 * @param id Unique ID of the service
 * @param meta Arbitrary KV metadata linked to the service instance.
 * @param checks Specify a list of checks
 *
 * @author Sergio del Amo
 * @since 4.1.0
 */
@Serdeable
public record ConsulNewServiceEntry(
    @JsonProperty("Name")
    String name,

    @Nullable
    @JsonProperty("Address")
    String address,

    @Nullable
    @JsonProperty("Port")
    Integer port,

    @Nullable
    @JsonProperty("Tags")
    List<String> tags,

    @Nullable
    @JsonProperty("ID")
    String id,

    @Nullable
    @JsonProperty("Meta")
    Map<String, String> meta,

    @Nullable
    @JsonProperty("Checks")
    List<ConsulCheck> checks
    ) {
}
