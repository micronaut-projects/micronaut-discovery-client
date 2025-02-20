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
package io.micronaut.discovery.consul.client.v1.blockingqueries;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.http.annotation.FilterMatcher;

/**
 * Annotation to handle the {@code wait} parameter for <a href="https://developer.hashicorp.com/consul/api-docs/features/blocking">Consul Blocking Queries</a>.
 *
 * @author LE GALL Benoît
 * @see BlockedQueryClientFilter
 * @see BlockingQueriesConfiguration
 * @since 4.6.0
 */
@FilterMatcher
@Documented
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface BlockedQueries {
}
