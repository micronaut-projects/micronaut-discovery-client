/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.Toggleable;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.HttpClientFilter;

import java.util.Optional;

/**
 * A {@link HttpClientFilter} that adds the {@link #HEADER_CONSUL_TOKEN} header.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@ClientFilter(patterns = "/v1/**", serviceId = ConsulClient.SERVICE_ID)
@Requires(beans = ConsulConfiguration.class)
@BootstrapContextCompatible
public final class ConsulAslTokenClientFilter implements Toggleable {

    /**
     * Consult header token.
     */
    public static final String HEADER_CONSUL_TOKEN = "X-Consul-Token";

    private final ConsulConfiguration configuration;

    /**
     * @param configuration The Consul configuration
     */
    public ConsulAslTokenClientFilter(ConsulConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean isEnabled() {
        return configuration.getAslToken().isPresent();
    }

    @RequestFilter
    void filterRequest(MutableHttpRequest<?> request) {
        Optional<String> aslToken = configuration.getAslToken();
        aslToken.ifPresent(token -> request.header(HEADER_CONSUL_TOKEN, token));
    }
}
