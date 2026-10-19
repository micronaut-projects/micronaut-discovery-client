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
import io.micronaut.discovery.spring.config.client.ConfigServerPropertySource;
import io.micronaut.discovery.spring.config.client.ConfigServerResponse;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Checks that the Spring Cloud Config model reads the JSON sent by the Spring Cloud Config Server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class SpringCloudConfigJsonTest {

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
    void readEnvironment() throws Exception {
        ConfigServerResponse response = jsonMapper.readValue(SpringCloudConfigPayloads.ENVIRONMENT, ConfigServerResponse.class);

        assertNotNull(response);
        assertEquals("myapp", response.getName());
        assertArrayEquals(new String[] {"first", "second"}, response.getProfiles());
        assertEquals("main", response.getLabel());
        assertEquals("b071c1570f225343ac49823e67bdc85cba52ca1d", response.getVersion());
        assertNull(response.getState());

        List<ConfigServerPropertySource> propertySources = response.getPropertySources();
        assertEquals(3, propertySources.size());
        ConfigServerPropertySource second = propertySources.get(0);
        assertEquals("https://example.com/config-repo.git/myapp-second.yml", second.getName());
        assertEquals(Map.of("greeting", "hello from second", "list[0]", "a", "list[1]", "b"), second.getSource());
        Map<String, Object> first = propertySources.get(1).getSource();
        assertEquals("hello from first", first.get("greeting"));
        assertEquals(42, ((Number) first.get("number")).intValue());
        assertEquals(true, first.get("enabled"));
        assertEquals("deep", first.get("nested.value"));
        assertEquals("https://example.com/config-repo.git/application.yml", propertySources.get(2).getName());
    }

    @Test
    void readEmptyEnvironment() throws Exception {
        ConfigServerResponse response = jsonMapper.readValue("""
            {"name": "unknown", "profiles": ["default"], "label": null, "version": null, "state": null, "propertySources": []}""",
            ConfigServerResponse.class);

        assertEquals("unknown", response.getName());
        assertArrayEquals(new String[] {"default"}, response.getProfiles());
        assertNull(response.getLabel());
        assertEquals(List.of(), response.getPropertySources());
    }

    @Test
    void readPropertySourceWithoutSource() throws Exception {
        ConfigServerPropertySource propertySource = jsonMapper.readValue("""
            {"name": "empty.yml"}""", ConfigServerPropertySource.class);

        assertEquals("empty.yml", propertySource.getName());
        assertEquals(Map.of(), propertySource.getSource());
    }
}
