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
package io.micronaut.discovery.eureka.client.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Models application info exposed by Eureka.
 * <p>
 * The Eureka REST API wraps a single application in an {@code application} member. The {@link EurekaClient} adds
 * and removes that wrapper, so this type maps the application itself.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationInfo {

    private String name;

    private List<InstanceInfo> instances;

    /**
     * @param name      The name
     * @param instances The instances
     */
    @JsonCreator
    ApplicationInfo(@JsonProperty("name") String name,
                    @JsonProperty("instance") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                    List<InstanceInfo> instances) {
        this.name = name;
        this.instances = instances;
    }

    /**
     * @return The application name
     */
    public String getName() {
        return name;
    }

    /**
     * @return The instances of this application
     */
    @JsonProperty("instance")
    public List<InstanceInfo> getInstances() {
        return instances;
    }

    @Override
    public String toString() {
        return "ApplicationInfo{" +
            "name='" + name + '\'' +
            ", instances=" + instances +
            '}';
    }
}
