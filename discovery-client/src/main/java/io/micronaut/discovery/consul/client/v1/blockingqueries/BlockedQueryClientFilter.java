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

import jakarta.inject.Singleton;

import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;

/**
 * Filter to add the {@code wait} parameter for <a href="https://developer.hashicorp.com/consul/api-docs/features/blocking">Consul Blocking Queries</a> for request annotated with {@link BlockedQueries}.
 *
 * @author LE GALL Benoît
 * @see BlockedQueries
 * @see BlockingQueriesConfiguration
 * @since 4.6.0
 */
@BlockedQueries
@Singleton
@ClientFilter
public final class BlockedQueryClientFilter {

    static final String PARAMETER_INDEX = "index";
    static final String PARAMETER_WAIT = "wait";

    private final BlockingQueriesConfiguration blockingQueriesConfiguration;

    public BlockedQueryClientFilter(
        final BlockingQueriesConfiguration blockingQueriesConfiguration) {
        this.blockingQueriesConfiguration = blockingQueriesConfiguration;
    }

    @RequestFilter
    void filterRequest(final MutableHttpRequest<?> request) {
        final var parameters = request.getParameters();
        if (parameters.contains(PARAMETER_INDEX)) {
            parameters.add(PARAMETER_WAIT, blockingQueriesConfiguration.getMaxWaitDuration());
        }
    }
}
