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

import java.util.List;

import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.KeyValue;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import reactor.core.publisher.Mono;

/**
 * A non-blocking HTTP client for Consul, using <a href="https://developer.hashicorp.com/consul/api-docs/features/blocking">Blocking Queries</a> feature
 * to wait for potential changes using long polling.
 *
 * @author LE GALL Benoît
 * @since 4.6.0
 */
@BlockedQueries
@Requires(beans = BlockingQueriesConfiguration.class)
@Client(id = ConsulClient.SERVICE_ID, path = "/v1",
        configuration = BlockingQueriesConfiguration.class)
public interface BlockedQueriesConsulClient {

    /**
     * Reads a Key from Consul. See <a href="https://developer.hashicorp.com/consul/api-docs/kv#read-key">KV Store - ReadKey API</a>.
     *
     * @param key     The key to watch
     * @param recurse Whether the lookup is recursive or not. When {@code true}, treat {@code key} as a prefix
     * @param index   The index value against which to wait for subsequent changes
     * @return A {@link Publisher} that emits a list of {@link KeyValue}
     */
    @Get(uri = "/kv/{+key}?{&recurse}{&index}", single = true)
    Mono<List<KeyValue>> watchValues(String key,
                                     @Nullable @QueryValue Boolean recurse,
                                     @Nullable @QueryValue Integer index);

}
