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
package io.micronaut.discovery.spring.suite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads configuration from a Spring Cloud Config Server stub with the bootstrap configuration client.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class SpringCloudConfigBootstrapTest {

    private EmbeddedServer configServer;

    @BeforeAll
    void start() {
        configServer = ApplicationContext.run(EmbeddedServer.class, Map.of(SpringCloudConfigStubServer.ENABLED, true));
    }

    @AfterAll
    void stop() {
        if (configServer != null) {
            configServer.close();
        }
    }

    @Test
    void readEnvironment() {
        try (ApplicationContext context = run("myapp", Map.of())) {
            assertEquals("hello from second", context.getRequiredProperty("greeting", String.class));
            assertEquals("first", context.getRequiredProperty("first.only", String.class));
            assertEquals("from application", context.getRequiredProperty("shared", String.class));
            assertEquals(42, context.getRequiredProperty("number", Integer.class));
            assertEquals("deep", context.getRequiredProperty("nested.value", String.class));
            assertEquals(List.of("a", "b"), context.getRequiredProperty("list", List.class));
        }
    }

    @Test
    void readEnvironmentFromSecuredServer() {
        try (ApplicationContext context = run(SpringCloudConfigStubServer.SECURED_APPLICATION, Map.of(
            "spring.cloud.config.username", SpringCloudConfigStubServer.USERNAME,
            "spring.cloud.config.password", SpringCloudConfigStubServer.PASSWORD,
            "spring.cloud.config.label", SpringCloudConfigStubServer.LABEL
        ))) {
            assertEquals("from secured", context.getRequiredProperty("secured.value", String.class));
            assertEquals(SpringCloudConfigStubServer.LABEL, context.getRequiredProperty("config.label", String.class));
        }
    }

    private ApplicationContext run(String applicationName, Map<String, Object> extraProperties) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("micronaut.application.name", applicationName);
        properties.put("micronaut.config-client.enabled", true);
        properties.put("spring.cloud.config.enabled", true);
        properties.put("spring.cloud.config.uri", "http://localhost:" + configServer.getPort());
        properties.putAll(extraProperties);
        return ApplicationContext.builder()
            .bootstrapEnvironment(true)
            .environments("first", "second")
            .properties(properties)
            .start();
    }
}
