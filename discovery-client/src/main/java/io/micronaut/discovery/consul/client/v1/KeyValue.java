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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;

/**
 * Represents a Key/Value pair returned from Consul via /kv/:key.
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Serdeable
@ReflectiveAccess
public class KeyValue {
    @JsonProperty("Key")
    private final String key;

    @JsonProperty("Value")
    private final String value;

    @Nullable
    @JsonProperty("ModifyIndex")
    private final Integer modifyIndex;

    /**
     * @param modifyIndex The KV index
     * @param key   The key
     * @param value The value
     */
    @Creator
    public KeyValue(String key, String value, Integer modifyIndex) {
        this.key = key;
        this.value = value;
        this.modifyIndex = modifyIndex;
    }

    /**
     * @param key   The key
     * @param value The value
     * @deprecated Use {@link KeyValue#KeyValue(String, String, Integer)} instead.
     */
    @Deprecated(forRemoval = true, since = "4.6.0")
    public KeyValue(String key, String value) {
        this(key, value, null);
    }

    /**
     * @return The modifyIndex
     */
    public Integer getModifyIndex() {
        return modifyIndex;
    }

    /**
     * @return The key
     */
    public String getKey() {
        return key;
    }

    /**
     * @return The value
     */
    public String getValue() {
        return value;
    }
}
