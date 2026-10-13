/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.discovery.consul.discovery;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.ConsulHealthEntry;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Client issuing blocking queries on the instances of a Consul service. Uses
 * {@link ConsulServiceWatchConfiguration} so that the read timeout outlasts the blocking query wait.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Requires(beans = ConsulServiceWatchConfiguration.class)
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = ConsulServiceWatchConfiguration.class)
public interface ConsulServiceWatchClient {

    /**
     * The response header carrying the index to block on.
     */
    String HEADER_CONSUL_INDEX = "X-Consul-Index";

    /**
     * Lists the instances of a service, blocking until they change when an index is given.
     *
     * @param service    The service
     * @param passing    Whether only passing instances are returned
     * @param tag        The tags, sent as repeated parameters
     * @param dc         The data center
     * @param filter     The filter expression
     * @param consistent The consistent flag
     * @param stale      The stale flag
     * @param index      The index to block on
     * @param wait       The maximum time to block
     * @return The response, whose {@value #HEADER_CONSUL_INDEX} header carries the index of the result
     */
    @Get(uri = "/health/service/{service}{?passing,tag*,dc,filter,consistent,stale,index,wait}")
    Mono<HttpResponse<List<ConsulHealthEntry>>> watchHealthService(
        @NotNull String service,
        @Nullable Boolean passing,
        @Nullable List<String> tag,
        @Nullable String dc,
        @Nullable String filter,
        @Nullable Boolean consistent,
        @Nullable Boolean stale,
        @Nullable Long index,
        @Nullable String wait);
}
