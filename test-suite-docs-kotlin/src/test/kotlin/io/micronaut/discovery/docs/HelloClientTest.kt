package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "HelloClientTest")
@MicronautTest
class HelloClientTest {

    @Inject
    lateinit var helloClient: HelloClient

    @Inject
    lateinit var helloService: HelloService

    @Test
    fun theDeclarativeClientResolvesTheHelloWorldService() {
        assertEquals("Hello World", helloClient.hello())
    }

    @Test
    fun theInjectedHttpClientResolvesTheHelloWorldService() {
        assertEquals("Hello World", helloService.hello())
    }
}
