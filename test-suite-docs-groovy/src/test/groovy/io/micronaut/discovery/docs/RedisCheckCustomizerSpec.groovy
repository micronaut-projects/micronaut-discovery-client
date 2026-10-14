package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulCheck
import io.micronaut.discovery.consul.registration.ConsulCheckCustomizer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "RedisCheckCustomizerSpec")
@MicronautTest(startApplication = false)
class RedisCheckCustomizerSpec extends Specification {

    @Inject
    List<ConsulCheckCustomizer> customizers

    void "the customizer adds the redis check"() {
        given:
        List<ConsulCheck> checks = []

        expect:
        customizers.size() == 1
        customizers[0] instanceof RedisCheckCustomizer

        when:
        customizers[0].customize(ServiceInstance.of("my-service", "localhost", 8080), "my-service:8080", checks)

        then:
        checks.size() == 1
        checks[0].id == "service:my-service:8080:redis"
        checks[0].name == "redis"
        checks[0].tcp == "localhost:6379"
        checks[0].interval == "10s"
    }
}
