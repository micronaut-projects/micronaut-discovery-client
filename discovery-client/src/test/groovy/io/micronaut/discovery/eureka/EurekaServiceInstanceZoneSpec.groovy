package io.micronaut.discovery.eureka

import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class EurekaServiceInstanceZoneSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "the zone of an instance is read from its metadata"() {
        when:
        EurekaServiceInstance instance = instance(MY_OWN, '{"zone": "zone-a"}')

        then:
        instance.zone.get() == 'zone-a'
        !instance.region.present
        !instance.group.present
    }

    void "the availability zone of an AWS instance wins over the metadata"() {
        when:
        EurekaServiceInstance instance = instance(amazon('"availability-zone": "us-east-1a"'), '{"zone": "zone-a"}')

        then:
        instance.zone.get() == 'us-east-1a'
    }

    void "the zone of an AWS instance without availability zone is read from its metadata"() {
        when:
        EurekaServiceInstance instance = instance(amazon('"instance-id": "i-0123456789"'), '{"zone": "zone-a"}')

        then:
        instance.zone.get() == 'zone-a'
    }

    void "the region and group of an instance are read from its metadata"() {
        when:
        EurekaServiceInstance instance = instance(MY_OWN, '{"zone": "zone-a", "region": "region-1", "group": "group-1"}')

        then:
        instance.zone.get() == 'zone-a'
        instance.region.get() == 'region-1'
        instance.group.get() == 'group-1'
    }

    void "an instance without zone has none"() {
        when:
        EurekaServiceInstance instance = instance(MY_OWN, '{"management.port": "8080"}')

        then:
        !instance.zone.present
    }

    private static final String MY_OWN = '''{
        "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
        "name": "MyOwn"
    }'''

    private static String amazon(String metadata) {
        """{
        "@class": "com.netflix.appinfo.AmazonInfo",
        "name": "Amazon",
        "metadata": {$metadata}
    }"""
    }

    private EurekaServiceInstance instance(String dataCenterInfo, String metadata) {
        String json = """{
          "instanceId": "module1:8080",
          "hostName": "module1-001",
          "app": "MODULE1",
          "ipAddr": "172.20.0.5",
          "status": "UP",
          "port": {"\$": 8080, "@enabled": "true"},
          "securePort": {"\$": 8443, "@enabled": "false"},
          "dataCenterInfo": $dataCenterInfo,
          "metadata": $metadata,
          "vipAddress": "module1"
        }"""
        return new EurekaServiceInstance(jsonMapper.readValue(json, InstanceInfo))
    }
}
