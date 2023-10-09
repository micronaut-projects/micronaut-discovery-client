package io.micronaut.discovery.consul

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ConsulNewServiceEntrySpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "ConsulServiceEntry is annotated with @Introspected"() {
        when:
        BeanIntrospection.getIntrospection(ConsulNewServiceEntry)

        then:
        noExceptionThrown()
    }

    void "serialization and deserialization of JSON payload to ConsulNewServiceEntry"() {
        given:
        String json = '''\
{
  "ID": "redis1",
  "Name": "redis",
  "Tags": ["primary", "v1"],
  "Address": "127.0.0.1",
  "Port": 8000,
  "Meta": {
    "redis_version": "4.0"
  },
  "EnableTagOverride": false,
  "Check": {
    "DeregisterCriticalServiceAfter": "90m",
    "Args": ["/usr/local/bin/check_redis.py"],
    "Interval": "10s",
    "Timeout": "5s"
  },
  "Weights": {
    "Passing": 10,
    "Warning": 1
  }
}'''
        when:
        ConsulNewServiceEntry serviceEntry = jsonMapper.readValue(json, ConsulNewServiceEntry)

        then:
        serviceEntry.name()  == 'redis'
        serviceEntry.id()  == 'redis1'
        serviceEntry.address() == "127.0.0.1"
        serviceEntry.meta()  == ['redis_version': '4.0']
        serviceEntry.tags()  == ['primary', 'v1']

        when:
        String jsonOutput = jsonMapper.writeValueAsString(serviceEntry)

        then:
        jsonOutput

        jsonOutput.contains('"ID":"redis1"')
        jsonOutput.contains('"Name":"redis"')
        jsonOutput.contains('"Port":8000')
        jsonOutput.contains('"Tags":["primary","v1"]')
        jsonOutput.contains('"Meta":{"redis_version":"4.0"}')
    }
}
