package io.micronaut.discovery.consul.health

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.consul.MockConsulServer
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ConsulHealthIndicatorSpec extends Specification {

    void "by default only the leader is reported"() {
        given:
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [(MockConsulServer.ENABLED): true])
        ApplicationContext context = ApplicationContext.run([
                'consul.client.host': consulServer.host,
                'consul.client.port': consulServer.port
        ])

        when:
        HealthResult result = Mono.from(context.getBean(ConsulHealthIndicator).result).block()

        then:
        result.name == 'consul'
        result.status == HealthStatus.UP
        result.details == [leader: 'localhost']

        cleanup:
        context?.close()
        consulServer?.close()
    }

    void "the catalog services and the registered service checks are reported when enabled"() {
        given:
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [(MockConsulServer.ENABLED): true])
        EmbeddedServer application = ApplicationContext.run(EmbeddedServer, [
                'consul.client.host'                 : consulServer.host,
                'consul.client.port'                 : consulServer.port,
                'consul.client.health-check-services': true,
                'consul.client.registration.tags'    : ['health'],
                'micronaut.application.name'         : 'health-service'
        ])
        ConsulClient consulClient = application.applicationContext.getBean(ConsulClient)
        ConsulHealthIndicator indicator = application.applicationContext.getBean(ConsulHealthIndicator)
        new PollingConditions(timeout: 10).eventually {
            assert Flux.from(consulClient.getInstances('health-service')).blockFirst().size() == 1
        }

        when:
        HealthResult result = Mono.from(indicator.result).block()
        Map<String, Object> details = result.details as Map<String, Object>

        then:
        result.status == HealthStatus.UP
        details.leader == 'localhost'
        (details.services as Map)['health-service'] == ['health']
        details.service.name == 'health-service'
        details.service.status == 'passing'
        details.service.instances.size() == 1
        details.service.instances[0].port == application.port
        details.service.instances[0].checks[0].status == 'passing'

        when: "the service check of the registered instance fails"
        Mono.from(consulClient.fail("service:health-service:${application.port}")).block()
        result = Mono.from(indicator.result).block()
        details = result.details as Map<String, Object>

        then: "the check status is reported but does not change the Consul status, to avoid a feedback loop with HTTP checks"
        result.status == HealthStatus.UP
        details.service.status == 'critical'
        details.service.instances[0].status == 'critical'
        details.service.instances[0].checks[0].status == 'critical'

        cleanup:
        application?.close()
        consulServer?.close()
    }

    void "an unreachable Consul agent is reported as DOWN"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'consul.client.host'                 : 'localhost',
                'consul.client.port'                 : 1,
                'consul.client.health-check-services': true,
                'micronaut.application.name'         : 'health-service'
        ])

        when:
        HealthResult result = Mono.from(context.getBean(ConsulHealthIndicator).result).block()

        then:
        result.status == HealthStatus.DOWN
        (result.details as Map).error

        cleanup:
        context.close()
    }
}
