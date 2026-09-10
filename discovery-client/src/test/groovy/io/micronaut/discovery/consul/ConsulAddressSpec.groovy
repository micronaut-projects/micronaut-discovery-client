package io.micronaut.discovery.consul

import io.micronaut.core.type.Argument
import io.micronaut.discovery.consul.client.v1.ConsulCatalogEntry
import io.micronaut.discovery.consul.client.v1.ConsulHealthEntry
import io.micronaut.discovery.consul.client.v1.MemberEntry
import io.micronaut.discovery.consul.client.v1.NewServiceEntry
import io.micronaut.discovery.consul.client.v1.NodeEntry
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Consul addresses may be host names. They must be read and written as strings, without
 * depending on how the JSON library handles {@link InetAddress}: Jackson 3.2 only accepts IP literals.
 */
@MicronautTest(startApplication = false)
class ConsulAddressSpec extends Specification {

    // the .invalid top level domain never resolves, so a lookup during deserialization would fail
    static final String HOST = 'consul-node.invalid'

    @Inject
    JsonMapper jsonMapper

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

        when: 'the service has no address of its own'
        ConsulServiceInstance instance = new ConsulServiceInstance(entries[0], 'http')

        then: 'the node host name is used as it is'
        instance.URI == URI.create("http://$HOST:8000")

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

    void "a new service entry keeps a host name address for registration"() {
        given:
        NewServiceEntry entry = new NewServiceEntry('test-service').address(HOST).port(8080)

        when:
        String output = jsonMapper.writeValueAsString(entry)

        then:
        output.contains(/"Address":"$HOST"/)
        output.count('Address') == 1

        when:
        NewServiceEntry read = jsonMapper.readValue(output, NewServiceEntry)

        then:
        read.hostString.get() == HOST
        read.port.asInt == 8080
    }

    void "the InetAddress accessors still work"() {
        given:
        InetAddress ip = InetAddress.getByName('10.1.10.12')
        InetAddress named = InetAddress.getByAddress('consul.example', ip.address)

        expect: 'an address is stored as its host name, or as its IP literal when it has none'
        new NodeEntry('foobar', ip).hostString == '10.1.10.12'
        new NodeEntry('foobar', named).hostString == 'consul.example'
        new ConsulCatalogEntry('foobar', named, null, null, null, null).address() == 'consul.example'
        new MemberEntry().tap { address = ip }.hostString == '10.1.10.12'
        new NewServiceEntry('test-service').address(named).hostString.get() == 'consul.example'

        and: 'IP literals resolve without a lookup'
        new NodeEntry('foobar', '10.1.10.12').address == ip
        new MemberEntry().tap { hostString = '10.1.10.12' }.address == ip
        new NewServiceEntry('test-service').address('10.1.10.12').address.get() == ip
        new NodeEntry('foobar', (String) null).address == null
    }
}
