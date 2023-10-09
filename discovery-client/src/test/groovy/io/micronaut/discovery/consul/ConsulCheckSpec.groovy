package io.micronaut.discovery.consul

import io.micronaut.discovery.consul.client.v1.ConsulCheck
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ConsulCheckSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "sample payload"() {
        given:
        ConsulCheck check = new ConsulCheck()
        check.id = "mem"
        check.name = "Memory utilization"
        check.notes = "Ensure we don't oversubscribe memory"
        check.deregisterCriticalServiceAfter = "90m"
        check.http = new URL("https://example.com")
        check.method = "POST"
        check.header = ["Content-Type": ["application/json"]]
        check.interval = "10s"
        check.tlsSkipVerify = true

        when:
        String json = jsonMapper.writeValueAsString(check)

        then:
        json
        json.contains('"CheckID":"mem"')
        json.contains('"Name":"Memory utilization"')
        json.contains("\"Notes\":\"Ensure we don't oversubscribe memory\"")
        json.contains('"DeregisterCriticalServiceAfter":"90m"')
        json.contains('"HTTP":"https://example.com"')
        json.contains('"Method":"POST"')
        json.contains('"Header":{"Content-Type":["application/json"]')
        json.contains('"Interval":"10s"')
        json.contains('"TLSSkipVerify":true')
    }
}
