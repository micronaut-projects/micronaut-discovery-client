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
import io.micronaut.discovery.vault.config.v1.VaultResponseV1;
import io.micronaut.discovery.vault.config.v2.VaultResponseData;
import io.micronaut.discovery.vault.config.v2.VaultResponseV2;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Checks that the Vault model reads the JSON sent by the Vault server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class VaultJsonTest {

    private ApplicationContext context;
    private JsonMapper jsonMapper;

    @BeforeAll
    void start() {
        context = ApplicationContext.run();
        jsonMapper = context.getBean(JsonMapper.class);
    }

    @AfterAll
    void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void readKvV1Response() throws Exception {
        VaultResponseV1 response = jsonMapper.readValue(VaultPayloads.KV_V1_SECRET, VaultResponseV1.class);

        assertNotNull(response);
        assertEquals("8d6d4bd1-cd4f-31a6-55aa-26b5b1fd3c87", response.getRequestId());
        assertEquals("", response.getLeaseId());
        assertEquals(2764800L, response.getLeaseDuration());
        assertFalse(response.isRenewable());
        assertNull(response.getWrapInfo());
        Map<String, Object> secrets = response.getSecrets();
        assertEquals("hello from kv v1", secrets.get("greeting"));
        assertEquals(42, ((Number) secrets.get("number")).intValue());
        assertEquals(true, secrets.get("enabled"));
        assertEquals(Map.of("value", "deep v1"), secrets.get("nested"));
    }

    @Test
    void readKvV2Response() throws Exception {
        VaultResponseV2 response = jsonMapper.readValue(VaultPayloads.KV_V2_SECRET, VaultResponseV2.class);

        assertNotNull(response);
        assertEquals("f1b6f6c2-5a7e-8d2c-0b8c-6c1e1a3e4f5a", response.getRequestId());
        assertEquals(0L, response.getLeaseDuration());
        assertEquals(List.of("a warning"), response.getWarnings());
        VaultResponseData data = response.getData();
        assertNotNull(data);
        assertEquals(2, ((Number) data.getMetadata().get("version")).intValue());
        assertEquals(Map.of("owner", "jdoe"), data.getMetadata().get("custom_metadata"));
        Map<String, Object> secrets = response.getSecrets();
        assertEquals("hello from kv v2", secrets.get("greeting"));
        assertEquals(7, ((Number) secrets.get("number")).intValue());
        assertEquals(true, secrets.get("enabled"));
        assertEquals(Map.of("value", "deep v2"), secrets.get("nested"));
    }

    @Test
    void readKvV2ResponseWithoutMetadata() throws Exception {
        VaultResponseV2 response = jsonMapper.readValue("""
            {"data": {"data": {"key": "value"}}}""", VaultResponseV2.class);

        assertEquals(Map.of("key", "value"), response.getSecrets());
        assertEquals(Map.of(), response.getData().getMetadata());
    }
}
