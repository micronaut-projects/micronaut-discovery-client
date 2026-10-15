package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulCheck
import io.micronaut.discovery.consul.registration.ConsulCheckCustomizer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "RedisCheckCustomizerTest")
@MicronautTest(startApplication = false)
class RedisCheckCustomizerTest {

    @Inject
    lateinit var customizers: List<ConsulCheckCustomizer>

    @Test
    fun theCustomizerAddsTheRedisCheck() {
        assertEquals(1, customizers.size)
        val customizer = customizers.first()
        assertInstanceOf(RedisCheckCustomizer::class.java, customizer)

        val checks = mutableListOf<ConsulCheck>()
        customizer.customize(ServiceInstance.of("my-service", "localhost", 8080), "my-service:8080", checks)

        assertEquals(1, checks.size)
        val check = checks.first()
        assertEquals("service:my-service:8080:redis", check.id)
        assertEquals("redis", check.name)
        assertEquals("localhost:6379", check.tcp)
        assertEquals("10s", check.interval)
    }
}
