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
package io.micronaut.discovery.spring;

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock Spring Cloud Config Server that echoes selected request headers back as configuration properties
 * so tests can assert what the client sent.
 */
@Controller("/")
@Requires(property = MockSpringCloudConfigHeaderEchoServer.ENABLED)
public class MockSpringCloudConfigHeaderEchoServer {

    public static final String ENABLED = "enable.mock.spring-cloud-config-header-echo";

    private static final List<String> ECHOED_HEADERS = List.of("X-Foo", "X-Bar", "X-Config-Token", "Authorization");

    @Get("/{applicationName}{/profiles}")
    @Produces(single = true)
    public ConfigServerResponse readValues(String applicationName, @Nullable String profiles, HttpHeaders headers) {
        return echo(applicationName, profiles, null, headers);
    }

    @Get("/{applicationName}{/profiles}{/label}")
    @Produces(single = true)
    public ConfigServerResponse readValues(String applicationName, @Nullable String profiles, @Nullable String label, HttpHeaders headers) {
        return echo(applicationName, profiles, label, headers);
    }

    private ConfigServerResponse echo(String applicationName, @Nullable String profiles, @Nullable String label, HttpHeaders headers) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("echo.application", applicationName);
        for (String header : ECHOED_HEADERS) {
            List<String> values = headers.getAll(header);
            if (!values.isEmpty()) {
                properties.put("echo." + header.toLowerCase(), String.join("|", values));
            }
        }
        ConfigServerResponse response = new ConfigServerResponse();
        response.setName(applicationName);
        response.setProfiles(profiles != null ? profiles.split(",") : new String[0]);
        response.setLabel(label);
        response.getPropertySources().add(new ConfigServerPropertySource(applicationName, properties) { });
        return response;
    }
}
