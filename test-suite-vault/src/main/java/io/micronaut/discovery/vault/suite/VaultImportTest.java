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
package io.micronaut.discovery.vault.suite;

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
 * Imports configuration from a Vault server stub with {@code micronaut.config.import}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class VaultImportTest {

    private EmbeddedServer vault;
    private VaultStubServer stub;

    @BeforeAll
    void start() {
        vault = ApplicationContext.run(EmbeddedServer.class, Map.of(VaultStubServer.ENABLED, true));
        stub = vault.getApplicationContext().getBean(VaultStubServer.class);
    }

    @AfterAll
    void stop() {
        if (vault != null) {
            vault.close();
        }
    }

    @Test
    void importKvV2Secret() {
        try (ApplicationContext context = run("vault://" + VaultStubServer.TOKEN + "@localhost:" + vault.getPort()
            + "/myapp?kv-version=V2&secret-engine-name=" + VaultStubServer.KV_V2_ENGINE)) {
            assertEquals("hello from kv v2", context.getRequiredProperty("greeting", String.class));
            assertEquals(7, context.getRequiredProperty("number", Integer.class));
            assertEquals(true, context.getRequiredProperty("enabled", Boolean.class));
            assertEquals("deep v2", context.getRequiredProperty("nested.value", String.class));
            assertTrue(stub.getRequests().contains(VaultStubServer.KV_V2_ENGINE + "/data/myapp"));
        }
    }

    @Test
    void importKvV1Secret() {
        try (ApplicationContext context = run("vault://" + VaultStubServer.TOKEN + "@localhost:" + vault.getPort()
            + "/myapp?kv-version=V1&secret-engine-name=" + VaultStubServer.KV_V1_ENGINE)) {
            assertEquals("hello from kv v1", context.getRequiredProperty("greeting", String.class));
            assertEquals(42, context.getRequiredProperty("number", Integer.class));
            assertEquals("deep v1", context.getRequiredProperty("nested.value", String.class));
        }
    }

    @Test
    void importSecretWithPathPrefix() {
        try (ApplicationContext context = run("vault://localhost:" + vault.getPort()
            + "/prefixed?token=" + VaultStubServer.TOKEN + "&kv-version=V2&secret-engine-name="
            + VaultStubServer.KV_V2_ENGINE + "&path-prefix=config")) {
            assertEquals("from prefix", context.getRequiredProperty("prefixed", String.class));
        }
    }

    @Test
    void importSecretsFromMultipleLocations() {
        String base = "vault://" + VaultStubServer.TOKEN + "@localhost:" + vault.getPort();
        String options = "?kv-version=V2&secret-engine-name=" + VaultStubServer.KV_V2_ENGINE;
        try (ApplicationContext context = ApplicationContext.builder()
            .properties(Map.of("micronaut.config.import", List.of(base + "/application" + options, base + "/myapp" + options)))
            .start()) {
            assertEquals("from application", context.getRequiredProperty("shared", String.class));
            assertEquals("deep v2", context.getRequiredProperty("nested.value", String.class));
        }
    }

    @Test
    void optionalImportOfMissingSecret() {
        try (ApplicationContext context = run("optional:vault://" + VaultStubServer.TOKEN + "@localhost:" + vault.getPort()
            + "/missing?kv-version=V2&secret-engine-name=" + VaultStubServer.KV_V2_ENGINE + "&retry-attempts=1")) {
            assertFalse(context.containsProperty("greeting"));
        }
    }

    @Test
    void importWithInvalidTokenFails() {
        assertThrows(ConfigurationException.class, () -> run("vault://wrong-token@localhost:" + vault.getPort()
            + "/myapp?kv-version=V2&secret-engine-name=" + VaultStubServer.KV_V2_ENGINE + "&retry-attempts=1").close());
    }

    private static ApplicationContext run(String location) {
        return ApplicationContext.run(Map.of("micronaut.config.import", location));
    }
}
