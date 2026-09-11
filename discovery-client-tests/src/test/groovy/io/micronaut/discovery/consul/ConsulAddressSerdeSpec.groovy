package io.micronaut.discovery.consul

import io.micronaut.core.type.Argument
import io.micronaut.discovery.consul.client.v1.ConsulCatalogEntry
import io.micronaut.discovery.consul.client.v1.ConsulHealthEntry
import io.micronaut.discovery.consul.client.v1.MemberEntry
import io.micronaut.discovery.consul.client.v1.NodeEntry
import io.micronaut.json.JsonMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Consul addresses may be host names. With Micronaut Serialization they must be read and written
 * as strings, like with Jackson Databind.
 */
@MicronautTest(startApplication = false)
class ConsulAddressSerdeSpec extends Specification {

    // the .invalid top level domain never resolves, so a lookup during deserialization would fail
    static final String HOST = 'consul-node.invalid'

    @Inject
    JsonMapper jsonMapper

    void "the JSON mapper is Micronaut Serialization"() {
        expect:
        jsonMapper instanceof ObjectMapper
    }

    void "a health entry with a host name node address is read and written unchanged"() {
        given:
        String json = """[{
  "Node": {"Node": "foobar", "Address": "$HOST", "Datacenter": "dc1"},
  "Service": {"ID": "redis", "Service": "redis", "Port": 8000},
  "Checks": []
}]"""

        when:
        List<ConsulHealthEntry> entries = jsonMapper.readValue(json, Argument.listOf(ConsulHealthEntry))

        then:
        entries.size() == 1
        entries[0].node().address() == HOST
        new ConsulServiceInstance(entries[0], 'http').URI == URI.create("http://$HOST:8000")

        when:
        String output = jsonMapper.writeValueAsString(entries[0].node())

        then:
        output.contains(/"Address":"$HOST"/)
        jsonMapper.readValue(output, ConsulCatalogEntry) == entries[0].node()
    }

    void "a member with a host name address is read and written unchanged"() {
        when:
        MemberEntry member = jsonMapper.readValue(/{"Name": "foobar", "Addr": "$HOST", "Port": 8301, "Status": 1}/, MemberEntry)

        then:
        member.hostString == HOST
        member.port == 8301

        when:
        String output = jsonMapper.writeValueAsString(member)

        then:
        output.contains(/"Addr":"$HOST"/)
        !output.contains('"Address"')
    }

    void "a node entry with a host name address is read and written unchanged"() {
        when:
        NodeEntry node = jsonMapper.readValue(/{"Node": "foobar", "Address": "$HOST"}/, NodeEntry)

        then:
        node.node == 'foobar'
        node.hostString == HOST

        when:
        String output = jsonMapper.writeValueAsString(node)

        then:
        output.contains(/"Address":"$HOST"/)
        output.count('Address') == 1
    }
}
