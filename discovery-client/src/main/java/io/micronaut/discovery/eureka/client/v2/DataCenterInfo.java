/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.micronaut.discovery.eureka.client.v2;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * A simple interface for indicating which <em>datacenter</em> a particular instance belongs.
 * <p>
 * The {@code @class} property holds the type names used by Eureka: {@code com.netflix.appinfo.AmazonInfo} for
 * {@link AmazonInfo} and {@code com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo} for any other data center.
 * Micronaut Serialization uses the {@link JsonSubTypes} names and Jackson Databind uses a custom type id resolver.
 *
 * @author Karthik Ranganathan
 */
@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@class", defaultImpl = MyDataCenterInfo.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = MyDataCenterInfo.class, name = DataCenterTypeInfoResolver.MY_DATA_CENTER_INFO_TYPE_MARKER),
    @JsonSubTypes.Type(value = AmazonInfo.class, name = DataCenterTypeInfoResolver.AMAZON_INFO_TYPE)
})
@JsonTypeIdResolver(DataCenterTypeInfoResolver.class)
public interface DataCenterInfo {

    /**
     * Different data centers related to the instances.
     */
    enum Name {
        Netflix,
        Amazon,
        MyOwn
    }

    /**
     * @return The name
     */
    @ReflectiveAccess
    Name getName();
}
