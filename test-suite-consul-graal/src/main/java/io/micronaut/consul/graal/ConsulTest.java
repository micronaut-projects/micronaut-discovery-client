/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "ConsulTest")
@MicronautTest
@SuppressWarnings({
    "java:S5960", // This is a TCK. Assertions are ok.
})
class ConsulTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void test() {
        String hello = client.toBlocking().retrieve("/hello/Micronaut");
        assertEquals("Hello Micronaut", hello);

        await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            HttpResponse<String> exchange = client.toBlocking().exchange("/api/hello/Micronaut", String.class, String.class);
            assertEquals(HttpStatus.OK, exchange.status());
            assertEquals("Hello Micronaut", exchange.body());
        });
    }
}
