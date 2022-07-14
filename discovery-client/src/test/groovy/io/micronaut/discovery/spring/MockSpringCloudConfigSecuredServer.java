/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.discovery.spring;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.simple.SimpleHttpResponseFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock cloud config server with simple Basic authentication implementation.
 * It expects only username 'user' identified by password 'secured'.
 */
@Controller("/")
@Requires(property = MockSpringCloudConfigSecuredServer.ENABLED)
public class MockSpringCloudConfigSecuredServer extends MockSpringCloudConfigServer {

    public static final String ENABLED = "enable.mock.spring-cloud-config-secured";

    @Override
    protected Publisher<ConfigServerResponse> getConfigServerResponse(
        String applicationName, String profiles, String label, String authorization) {

        checkAuthorization(authorization);

        return super.getConfigServerResponse(applicationName, profiles, label, authorization);

    }

    /**
     * Checks if Basic authorization header is provided and valid.
     * If not valid throws {@link HttpClientResponseException}.
     * This is simplified way of implementing Basic authentication in the mock server for the test.
     * @param authorization
     */
    private static void checkAuthorization(String authorization) {
        if (authorization == null) {
            throw new HttpClientResponseException("No authorization provided", new SimpleHttpResponseFactory().status(HttpStatus.UNAUTHORIZED));
        }
        if (!authorization.startsWith("Basic ")) {
            throw new HttpClientResponseException("Invalid authorization", new SimpleHttpResponseFactory().status(HttpStatus.UNAUTHORIZED));
        }
        authorization = authorization.replace("Basic ", "");
        String decoded = new String(Base64.getDecoder().decode(authorization));
        String[] parts = decoded.split(":");
        // For test purposes we simplify and expect given username and password
        if (parts.length == 2 && "user".equals(parts[0]) && "secured".equals(parts[1])) {
            return;
        }
        throw new HttpClientResponseException("Invalid user credentials", new SimpleHttpResponseFactory().status(HttpStatus.UNAUTHORIZED));
    }
}
