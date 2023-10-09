package io.micronaut.discovery.consul

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.discovery.consul.client.v1.ConsulCatalogEntry
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ConsulCatalogEntrySpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "ConsulCatalogEntry is annotated with @Introspected"() {
        when:
        BeanIntrospection.getIntrospection(ConsulCatalogEntry)

        then:
        noExceptionThrown()
    }

    void "serialization and deserialization of JSON payload to ConsulCatalogEntry"() {
        given:
        String json = '''\
{
  "Datacenter": "dc1",
  "ID": "40e4a748-2192-161a-0510-9bf59fe950b5",
  "Node": "t2.320",
  "Address": "192.168.10.10",
  "TaggedAddresses": {
    "lan": "192.168.10.10",
    "wan": "10.0.10.10"
  },
  "NodeMeta": {
    "somekey": "somevalue"
  },
  "Service": {
    "ID": "redis1",
    "Service": "redis",
    "Tags": ["primary", "v1"],
    "Address": "127.0.0.1",
    "TaggedAddresses": {
      "lan": {
        "address": "127.0.0.1",
        "port": 8000
      },
      "wan": {
        "address": "198.18.0.1",
        "port": 80
      }
    },
    "Meta": {
      "redis_version": "4.0"
    },
    "Port": 8000,
    "Namespace": "default"
  },
  "Check": {
    "Node": "t2.320",
    "CheckID": "service:redis1",
    "Name": "Redis health check",
    "Notes": "Script based health check",
    "Status": "passing",
    "ServiceID": "redis1",
    "Definition": {
      "TCP": "localhost:8888",
      "Interval": "5s",
      "Timeout": "1s",
      "DeregisterCriticalServiceAfter": "30s"
    },
    "Namespace": "default"
  },
  "SkipNodeUpdate": false
}'''

        when:
        ConsulCatalogEntry consulCatalogEntry = jsonMapper.readValue(json, ConsulCatalogEntry)

        then:
        'dc1' == consulCatalogEntry.datacenter()
        '192.168.10.10' == consulCatalogEntry.address().getHostAddress()
        [lan: "192.168.10.10", wan: "10.0.10.10"] == consulCatalogEntry.taggedAddresses()
        [somekey: "somevalue"] == consulCatalogEntry.nodeMetadata()
        consulCatalogEntry.service()
        '127.0.0.1' == consulCatalogEntry.service().address()
        'redis1' == consulCatalogEntry.service().id()
        ['primary', 'v1'] == consulCatalogEntry.service().tags()
        't2.320' == consulCatalogEntry.node()

        when:
        String jsonOutput = jsonMapper.writeValueAsString(consulCatalogEntry)

        then:
        jsonOutput
        jsonOutput.contains('"Datacenter":"dc1"')
        jsonOutput.contains('"Node":"t2.320"')
        jsonOutput.contains('"Address":"192.168.10.10"')
        jsonOutput.contains('"TaggedAddresses":{"lan":"192.168.10.10","wan":"10.0.10.10"}')
        jsonOutput.contains('"NodeMeta":{"somekey":"somevalue"}')
    }
}
