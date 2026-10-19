package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.client.v1.ConsulCheck;
import io.micronaut.discovery.consul.registration.ConsulCheckCustomizer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Property(name = "spec.name", value = "RedisCheckCustomizerTest")
@MicronautTest(startApplication = false)
class RedisCheckCustomizerTest {

    @Inject
    List<ConsulCheckCustomizer> customizers;

    @Test
    void theCustomizerAddsTheRedisCheck() {
        assertEquals(1, customizers.size());
        ConsulCheckCustomizer customizer = customizers.get(0);
        assertInstanceOf(RedisCheckCustomizer.class, customizer);

        List<ConsulCheck> checks = new ArrayList<>();
        customizer.customize(ServiceInstance.of("my-service", "localhost", 8080), "my-service:8080", checks);

        assertEquals(1, checks.size());
        ConsulCheck check = checks.get(0);
        assertEquals("service:my-service:8080:redis", check.getId());
        assertEquals("redis", check.getName());
        assertEquals("localhost:6379", check.getTcp());
        assertEquals("10s", check.getInterval());
    }
}
