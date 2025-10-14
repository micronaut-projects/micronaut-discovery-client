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
/**
 * This package contains a Consul Watcher that will update the Context when application properties are changed in a Consul KV.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@Configuration
@RequiresConsul
@Requires(property = WatchConfiguration.PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
package io.micronaut.discovery.consul.watch;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.condition.RequiresConsul;
