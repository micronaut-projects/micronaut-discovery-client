package io.micronaut.discovery.consul

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry
import io.micronaut.discovery.consul.client.v1.ConsulServiceEntry
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ConsulServiceEntrySpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "ConsulServiceEntry is annotated with @Introspected"() {
        when:
        BeanIntrospection.getIntrospection(ConsulServiceEntry)

        then:
        noExceptionThrown()
    }

    void "serialization and deserialization of JSON payload to ConsulServiceEntry"() {
        given:
        String json = '''\
{
  "redis": {
    "ID": "redis",
    "Service": "redis",
    "Tags": [],
    "TaggedAddresses": {
      "lan": {
        "address": "127.0.0.1",
        "port": 8000
      },
      "wan": {
        "address": "198.51.100.53",
        "port": 80
      }
    },
    "Meta": {
      "redis_version": "4.0"
    },
    "Namespace": "default",
    "Port": 8000,
    "Address": "",
    "EnableTagOverride": false,
    "Datacenter": "dc1",
    "Weights": {
      "Passing": 10,
      "Warning": 1
    }
  }
}'''

        when:
        Argument<Map<String, ConsulServiceEntry>> argument = Argument.mapOf(Argument.of(String), Argument.of(ConsulServiceEntry))
        Map<String, ConsulServiceEntry> serviceEntry = jsonMapper.readValue(json, argument)

        then:
        serviceEntry['redis'].service()  == 'redis'
        serviceEntry['redis'].id()  == 'redis'
        serviceEntry['redis'].address() == ""
        serviceEntry['redis'].meta()  == ['redis_version': '4.0']
        serviceEntry['redis'].tags()  == []

        when:
        String jsonOutput = jsonMapper.writeValueAsString(serviceEntry)

        then:
        jsonOutput

        jsonOutput.contains('"ID":"redis"')
        jsonOutput.contains('"Service":"redis"')
        jsonOutput.contains('"Port":8000')
        !jsonOutput.contains('"Address":""')
        !jsonOutput.contains('"Tags":[]')
        jsonOutput.contains('"Meta":{"redis_version":"4.0"}')
    }
}
