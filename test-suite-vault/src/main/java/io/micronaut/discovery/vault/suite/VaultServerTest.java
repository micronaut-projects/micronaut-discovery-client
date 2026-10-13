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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads configuration from a real Vault server, which mounts a KV version 2 engine at {@code secret}.
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(120)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class VaultServerTest {

    private static final String TOKEN = "server-token";

    @Container
    static final VaultContainer<?> VAULT = new VaultContainer<>("hashicorp/vault:1.16.1")
        .withVaultToken(TOKEN)
        .withInitCommand(
            "kv put secret/myapp greeting=hello-from-vault number=12",
            "kv put secret/application shared=from-application"
        );

    @Test
    void importSecret() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.config.import", "vault://" + TOKEN + "@" + VAULT.getHost() + ":" + VAULT.getFirstMappedPort()
                + "/myapp?kv-version=V2&secret-engine-name=secret"
        ))) {
            assertEquals("hello-from-vault", context.getRequiredProperty("greeting", String.class));
            assertEquals(12, context.getRequiredProperty("number", Integer.class));
        }
    }

    @Test
    void readSecretsWithBootstrapClient() {
        try (ApplicationContext context = ApplicationContext.builder()
            .bootstrapEnvironment(true)
            .properties(Map.of(
                "micronaut.application.name", "myapp",
                "micronaut.config-client.enabled", true,
                "vault.client.config.enabled", true,
                "vault.client.config.retry-count", 1,
                "vault.client.config.retry-delay", "10ms",
                "vault.client.kv-version", "V2",
                "vault.client.token", TOKEN,
                "vault.client.uri", VAULT.getHttpHostAddress()
            ))
            .start()) {
            assertEquals("hello-from-vault", context.getRequiredProperty("greeting", String.class));
            assertEquals("from-application", context.getRequiredProperty("shared", String.class));
        }
    }
}
