package io.micronaut.discovery.consul

import io.micronaut.discovery.consul.client.v1.ConsulCheckStatus
import io.micronaut.discovery.consul.client.v1.ConsulHealthEntry
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ConsulHealthEntrySpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "deserialization"() {
        String json = '''\
[
  {
    "Node": {
      "ID": "40e4a748-2192-161a-0510-9bf59fe950b5",
      "Node": "foobar",
      "Address": "10.1.10.12",
      "Datacenter": "dc1",
      "TaggedAddresses": {
        "lan": "10.1.10.12",
        "wan": "10.1.10.12"
      },
      "Meta": {
        "instance_type": "t2.medium"
      }
    },
    "Service": {
      "ID": "redis",
      "Service": "redis",
      "Tags": ["primary"],
      "Address": "10.1.10.12",
      "TaggedAddresses": {
        "lan": {
          "address": "10.1.10.12",
          "port": 8000
        },
        "wan": {
          "address": "198.18.1.2",
          "port": 80
        }
      },
      "Meta": {
        "redis_version": "4.0"
      },
      "Port": 8000,
      "Weights": {
        "Passing": 10,
        "Warning": 1
      },
      "Namespace": "default"
    },
    "Checks": [
      {
        "Node": "foobar",
        "CheckID": "service:redis",
        "Name": "Service 'redis' check",
        "Status": "passing",
        "Notes": "",
        "Output": "",
        "ServiceID": "redis",
        "ServiceName": "redis",
        "ServiceTags": ["primary"],
        "Namespace": "default"
      },
      {
        "Node": "foobar",
        "CheckID": "serfHealth",
        "Name": "Serf Health Status",
        "Status": "passing",
        "Notes": "",
        "Output": "",
        "ServiceID": "",
        "ServiceName": "",
        "ServiceTags": [],
        "Namespace": "default"
      }
    ]
  }
]'''

        when:
        ConsulHealthEntry consulHealthEntry = jsonMapper.readValue(json, ConsulHealthEntry)

        then: 'verify service'
        'redis' == consulHealthEntry.service().service()
        '10.1.10.12' == consulHealthEntry.service().address()
        8000 == consulHealthEntry.service().port()
        ['primary'] == consulHealthEntry.service().tags()
        'redis' == consulHealthEntry.service().id()
        [redis_version: '4.0'] == consulHealthEntry.service().meta()

        and: 'verify node'
        consulHealthEntry.node()
        'foobar' == consulHealthEntry.node().getNode()
        InetAddress.getByName('10.1.10.12') == consulHealthEntry.node().getAddress()
        consulHealthEntry.node().getDatacenter().isPresent()
        'dc1' == consulHealthEntry.node().getDatacenter().get()
        [lan: "10.1.10.12", wan: '10.1.10.12'] == consulHealthEntry.node().getTaggedAddresses()
        !consulHealthEntry.node().getNodeMetadata()

        and: 'verify checks'
        consulHealthEntry.checks()
        2 == consulHealthEntry.checks().size()
        consulHealthEntry.checks().any {
                    it.id == 'service:redis' &&
                    it.name == "Service 'redis' check" &&
                    it.status == ConsulCheckStatus.PASSING.toString()
        }
        consulHealthEntry.checks().any {
            it.id == 'serfHealth' &&
                    it.name == "Serf Health Status" &&
                    it.status == ConsulCheckStatus.PASSING.toString()
        }
    }
}
