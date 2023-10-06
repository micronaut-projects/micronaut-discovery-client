package io.micronaut.discovery.consul

import io.micronaut.discovery.consul.client.v1.ConsulCheckStatus
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification


@MicronautTest(startApplication = false)
class ConsulCheckStatusSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "test check status"(ConsulCheckStatus status, String expected) {
        given:
        expected == status.toString()

        where:
        status                     || expected
        ConsulCheckStatus.PASSING  || "passing"
        ConsulCheckStatus.CRITICAL || "critical"
        ConsulCheckStatus.WARNING  || "warning"
    }

    void "json serialization uses toString"(ConsulCheckStatus status, String expected) {
        given:
        expected == jsonMapper.writeValueAsString(status)

        where:
        status                     || expected
        ConsulCheckStatus.PASSING  || "passing"
        ConsulCheckStatus.CRITICAL || "critical"
        ConsulCheckStatus.WARNING  || "warning"
    }
}
