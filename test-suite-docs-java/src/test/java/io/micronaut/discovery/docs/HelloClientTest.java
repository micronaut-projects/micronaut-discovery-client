package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "HelloClientTest")
@MicronautTest
class HelloClientTest {

    @Inject
    HelloClient helloClient;

    @Inject
    HelloService helloService;

    @Test
    void theDeclarativeClientResolvesTheHelloWorldService() {
        assertEquals("Hello World", helloClient.hello());
    }

    @Test
    void theInjectedHttpClientResolvesTheHelloWorldService() {
        assertEquals("Hello World", helloService.hello());
    }
}
