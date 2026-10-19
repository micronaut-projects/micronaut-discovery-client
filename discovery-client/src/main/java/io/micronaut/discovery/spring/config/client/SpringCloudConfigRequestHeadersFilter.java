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
package io.micronaut.discovery.spring.config.client;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.spring.config.SpringCloudClientConfiguration;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adds the configured custom headers, the {@value #CONFIG_TOKEN_HEADER} header and bearer authorization
 * to every request sent to the Spring Cloud Config Server.
 *
 * @since 5.3.0
 */
@Internal
@Singleton
@SpringCloudConfigFilterMatcher
@BootstrapContextCompatible
@Requires(beans = SpringCloudClientConfiguration.class)
public final class SpringCloudConfigRequestHeadersFilter implements HttpClientFilter {

    /**
     * The header used by Spring Cloud Config Server to pass a token to backends such as Vault.
     */
    public static final String CONFIG_TOKEN_HEADER = "X-Config-Token";

    private final SpringCloudClientConfiguration configuration;

    /**
     * @param configuration The Spring Cloud Config client configuration
     */
    SpringCloudConfigRequestHeadersFilter(SpringCloudClientConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        for (Map.Entry<String, String> header : configuration.getHeaders().entrySet()) {
            request.getHeaders().set(header.getKey(), header.getValue());
        }
        configuration.getToken().ifPresent(token -> request.getHeaders().set(CONFIG_TOKEN_HEADER, token));
        configuration.getBearerToken().ifPresent(request::bearerAuth);
        return chain.proceed(request);
    }

    /**
     * Fails if more than one way of setting the {@code Authorization} header is configured.
     *
     * @param configuration The Spring Cloud Config client configuration
     * @throws ConfigurationException if Basic authentication, a bearer token or an {@code Authorization} header are combined
     */
    public static void validateAuthorization(SpringCloudClientConfiguration configuration) {
        List<String> configured = new ArrayList<>(3);
        if (configuration.getUsername().isPresent() || configuration.getPassword().isPresent()) {
            configured.add("'" + SpringCloudClientConfiguration.PREFIX + ".username'/'" + SpringCloudClientConfiguration.PREFIX + ".password'");
        }
        if (configuration.getBearerToken().isPresent()) {
            configured.add("'" + SpringCloudClientConfiguration.PREFIX + ".bearer-token'");
        }
        if (configuration.getHeaders().keySet().stream().anyMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase)) {
            configured.add("'" + SpringCloudClientConfiguration.PREFIX + ".headers.authorization'");
        }
        if (configured.size() > 1) {
            throw new ConfigurationException("Only one of '" + SpringCloudClientConfiguration.PREFIX + ".username'/'password', '"
                + SpringCloudClientConfiguration.PREFIX + ".bearer-token' or '" + SpringCloudClientConfiguration.PREFIX
                + ".headers.authorization' may be configured for the Spring Cloud Config Server, but found: " + String.join(" and ", configured));
        }
    }
}
