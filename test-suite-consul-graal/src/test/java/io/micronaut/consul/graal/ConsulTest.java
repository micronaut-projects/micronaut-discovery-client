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

@MicronautTest
@Property(name = "consul.client.default-zone", value = "${consul.host}:${consul.port}")
class ConsulTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void test() throws Exception {
        String hello = client.toBlocking().retrieve("/hello/Micronaut");
        assertEquals("Hello Micronaut", hello);

        await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).until(() -> {
            try {
                HttpResponse<String> exchange = client.toBlocking().exchange("/api/hello/Micronaut", String.class, String.class);
                System.out.println(exchange.status() + " " + exchange.body());
                return HttpStatus.OK == exchange.status() && "Hello Micronaut".equals(exchange.body());
            } catch (Exception e) {
                return false;
            }
        });
    }
}
