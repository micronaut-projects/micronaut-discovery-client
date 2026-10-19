package io.micronaut.discovery.consul

import io.micronaut.discovery.consul.client.v1.ConsulHealthEntry
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ConsulServiceInstanceZoneSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "the zone of a service instance is read from the service Meta"() {
        when:
        ConsulServiceInstance instance = instance('["primary"]', '{"zone": "zone-a"}')

        then:
        instance.zone.get() == 'zone-a'
        instance.metadata.get('zone', String).get() == 'zone-a'
    }

    void "the zone of a service instance is read from a zone tag"() {
        when:
        ConsulServiceInstance instance = instance('["zone=zone-b"]', '{}')

        then:
        instance.zone.get() == 'zone-b'
    }

    void "the zone in the service Meta wins over a zone tag"() {
        when:
        ConsulServiceInstance instance = instance('["zone=zone-b"]', '{"zone": "zone-a"}')

        then:
        instance.zone.get() == 'zone-a'
    }

    void "a service instance without zone has none"() {
        when:
        ConsulServiceInstance instance = instance('["primary"]', '{"redis_version": "4.0"}')

        then:
        !instance.zone.present
    }

    private ConsulServiceInstance instance(String tags, String meta) {
        String json = """\
{
  "Node": {
    "ID": "40e4a748-2192-161a-0510-9bf59fe950b5",
    "Node": "foobar",
    "Address": "10.1.10.12",
    "Datacenter": "dc1"
  },
  "Service": {
    "ID": "redis-1",
    "Service": "redis",
    "Tags": $tags,
    "Address": "10.1.10.12",
    "Meta": $meta,
    "Port": 8000
  },
  "Checks": []
}"""
        return new ConsulServiceInstance(jsonMapper.readValue(json, ConsulHealthEntry), 'http')
    }
}
