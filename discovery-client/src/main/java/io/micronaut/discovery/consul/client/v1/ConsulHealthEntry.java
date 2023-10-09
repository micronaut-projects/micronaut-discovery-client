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
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * <a href="https://developer.hashicorp.com/consul/api-docs/health#sample-response-2">Sample Response for list check for service </a>.
 *
 * @author sdelamo
 * @since 4.1.0
 * @param node Node
 * @param service Service
 * @param checks Health checks
 */
@Serdeable
public record ConsulHealthEntry(@JsonProperty("Node") NodeEntry node,
                                @JsonProperty("Service") ConsulServiceEntry service,
                                @JsonProperty("Checks") List<ConsulCheck> checks) {
}
