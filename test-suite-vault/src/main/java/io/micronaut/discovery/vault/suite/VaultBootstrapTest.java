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
import io.micronaut.discovery.vault.config.v1.VaultConfigHttpClientV1;
import io.micronaut.discovery.vault.config.v2.VaultConfigHttpClientV2;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads configuration from a Vault server stub with the bootstrap configuration client.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class VaultBootstrapTest {

    private static final String ENVIRONMENT = "vaultenv";

    private EmbeddedServer vault;

    @BeforeAll
    void start() {
        vault = ApplicationContext.run(EmbeddedServer.class, Map.of(VaultStubServer.ENABLED, true));
    }

    @AfterAll
    void stop() {
        if (vault != null) {
            vault.close();
        }
    }

    @Test
    void readKvV2Secrets() {
        try (ApplicationContext context = run("V2", VaultStubServer.KV_V2_ENGINE)) {
            // myapp/vaultenv overrides myapp, which overrides application
            assertEquals("hello from myapp vaultenv", context.getRequiredProperty("greeting", String.class));
            assertEquals("from application", context.getRequiredProperty("shared", String.class));
            assertEquals(7, context.getRequiredProperty("number", Integer.class));
            assertEquals("deep v2", context.getRequiredProperty("nested.value", String.class));
            assertTrue(context.containsBean(VaultConfigHttpClientV2.class));
        }
    }

    @Test
    void readKvV1Secrets() {
        try (ApplicationContext context = run("V1", VaultStubServer.KV_V1_ENGINE)) {
            assertEquals("hello from kv v1", context.getRequiredProperty("greeting", String.class));
            assertEquals("from v1 application", context.getRequiredProperty("shared", String.class));
            assertEquals("deep v1", context.getRequiredProperty("nested.value", String.class));
            assertTrue(context.containsBean(VaultConfigHttpClientV1.class));
        }
    }

    private ApplicationContext run(String kvVersion, String engine) {
        return ApplicationContext.builder()
            .bootstrapEnvironment(true)
            .environments(ENVIRONMENT)
            .properties(Map.of(
                "micronaut.application.name", "myapp",
                "micronaut.config-client.enabled", true,
                "vault.client.config.enabled", true,
                "vault.client.config.retry-count", 1,
                "vault.client.config.retry-delay", "10ms",
                "vault.client.kv-version", kvVersion,
                "vault.client.token", VaultStubServer.TOKEN,
                "vault.client.secret-engine-name", engine,
                "vault.client.uri", "http://localhost:" + vault.getPort()
            ))
            .start();
    }
}
