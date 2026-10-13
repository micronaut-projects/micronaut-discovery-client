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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Imports configuration from a Spring Cloud Config Server stub with {@code micronaut.config.import}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class SpringCloudConfigImportTest {

    private EmbeddedServer configServer;
    private SpringCloudConfigStubServer stub;

    @BeforeAll
    void start() {
        configServer = ApplicationContext.run(EmbeddedServer.class, Map.of(SpringCloudConfigStubServer.ENABLED, true));
        stub = configServer.getApplicationContext().getBean(SpringCloudConfigStubServer.class);
    }

    @AfterAll
    void stop() {
        if (configServer != null) {
            configServer.close();
        }
    }

    @Test
    void importEnvironment() {
        try (ApplicationContext context = run("springcloud://localhost:" + configServer.getPort() + "/myapp/first,second")) {
            // the property sources first in the response take precedence
            assertEquals("hello from second", context.getRequiredProperty("greeting", String.class));
            assertEquals("first", context.getRequiredProperty("first.only", String.class));
            assertEquals("from application", context.getRequiredProperty("shared", String.class));
            assertEquals(42, context.getRequiredProperty("number", Integer.class));
            assertEquals(true, context.getRequiredProperty("enabled", Boolean.class));
            assertEquals("deep", context.getRequiredProperty("nested.value", String.class));
            assertEquals(List.of("a", "b"), context.getRequiredProperty("list", List.class));
            assertTrue(stub.getRequests().contains("myapp/first,second"));
        }
    }

    @Test
    void importEnvironmentWithLabel() {
        try (ApplicationContext context = run("springcloud://localhost:" + configServer.getPort() + "/myapp/default?label="
            + SpringCloudConfigStubServer.LABEL)) {
            assertEquals("hello from myapp", context.getRequiredProperty("greeting", String.class));
            assertEquals(SpringCloudConfigStubServer.LABEL, context.getRequiredProperty("config.label", String.class));
        }
    }

    @Test
    void importEnvironmentFromSecuredServer() {
        try (ApplicationContext context = run("springcloud://" + SpringCloudConfigStubServer.USERNAME + ":"
            + SpringCloudConfigStubServer.PASSWORD + "@localhost:" + configServer.getPort() + "/"
            + SpringCloudConfigStubServer.SECURED_APPLICATION + "/default")) {
            assertEquals("from secured", context.getRequiredProperty("secured.value", String.class));
            assertEquals("from application", context.getRequiredProperty("shared", String.class));
        }
    }

    @Test
    void importEnvironmentFromSecuredServerWithoutCredentialsFails() {
        assertThrows(ConfigurationException.class, () -> run("springcloud://localhost:" + configServer.getPort() + "/"
            + SpringCloudConfigStubServer.SECURED_APPLICATION + "/default?retry-attempts=1").close());
    }

    @Test
    void importEmptyEnvironmentOfUnknownApplication() {
        // the server returns an environment without property sources
        assertThrows(ConfigurationException.class, () -> run("springcloud://localhost:" + configServer.getPort()
            + "/unknown/default").close());
        try (ApplicationContext context = run("optional:springcloud://localhost:" + configServer.getPort() + "/unknown/default")) {
            assertFalse(context.containsProperty("greeting"));
        }
    }

    @Test
    void optionalImportOfUnknownLabel() {
        try (ApplicationContext context = run("optional:springcloud://localhost:" + configServer.getPort()
            + "/myapp/default?label=missing&retry-attempts=1")) {
            assertFalse(context.containsProperty("greeting"));
        }
    }

    private static ApplicationContext run(String location) {
        return ApplicationContext.run(Map.of("micronaut.config.import", location));
    }
}
