/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.consul.graal;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.discovery.consul.client.v1.KeyValue;
import io.micronaut.discovery.consul.testcontainers.Consul;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest(startApplication = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeyValueTest implements TestPropertyProvider {
    @Override
    public @NonNull Map<String, String> getProperties() {
        return Consul.getProperties();
    }

    @Inject
    JsonMapper jsonMapper;

    @Test
    void testJsonSerializationOfKeyValue() throws IOException, JSONException {
        //given:
        String expectedJson = "{\"Key\":\"foo\",\"Value\":\"bar\",\"ModifyIndex\":1}";

        //when:
        KeyValue value = new KeyValue(1, "foo", "bar");
        String json = jsonMapper.writeValueAsString(value);

        //then:
        JSONAssert.assertEquals(
            expectedJson, json, JSONCompareMode.LENIENT);

        //when:
        value = jsonMapper.readValue(expectedJson, KeyValue.class);

        //then:
        assertNotNull(value);
        assertEquals(1, value.getModifyIndex());
        assertEquals("foo", value.getKey());
        assertEquals("bar", value.getValue());

        //when:
        value = new KeyValue(null, "foo", "bar");
        json = jsonMapper.writeValueAsString(value);

        //then:
        assertEquals("{\"Key\":\"foo\",\"Value\":\"bar\"}", json);
    }
}
