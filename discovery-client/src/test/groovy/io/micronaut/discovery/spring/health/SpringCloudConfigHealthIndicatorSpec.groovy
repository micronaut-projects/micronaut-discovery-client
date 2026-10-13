package io.micronaut.discovery.spring.health

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.spring.MockSpringCloudConfigSecuredServer
import io.micronaut.discovery.spring.MockSpringCloudConfigServer
import io.micronaut.health.HealthStatus
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

class SpringCloudConfigHealthIndicatorSpec extends Specification {

    private static final Map<String, Object> FAST_RETRY = [
            'spring.cloud.config.config.retry-count': 1,
            'spring.cloud.config.config.retry-delay': '10ms'
    ]

    void "a reachable config server is reported as UP with the located property sources"() {
        given:
        EmbeddedServer configServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigServer.ENABLED): true])
        ApplicationContext context = ApplicationContext.run([
                'micronaut.application.name': 'myapp',
                'spring.cloud.config.enabled': true,
                'spring.cloud.config.uri'    : configServer.URL.toString()
        ], 'first')

        when:
        HealthResult result = Mono.from(context.getBean(SpringCloudConfigHealthIndicator).result).block()
        Map<String, Object> details = result.details as Map<String, Object>

        then:
        result.name == 'spring-cloud-config'
        result.status == HealthStatus.UP
        details.name == 'myapp'
        (details.profiles as String).split(',').contains('first')
        details['property-sources'] == ['myapp[first]', 'application[first]', 'myapp', 'application']

        cleanup:
        context?.close()
        configServer?.close()
    }

    void "the result is cached for the configured time-to-live"() {
        given:
        EmbeddedServer configServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigServer.ENABLED): true])
        ApplicationContext context = ApplicationContext.run(FAST_RETRY + [
                'micronaut.application.name'                   : 'myapp',
                'spring.cloud.config.enabled'                   : true,
                'spring.cloud.config.uri'                       : configServer.URL.toString(),
                'spring.cloud.config.health-check-time-to-live': ttl
        ])
        SpringCloudConfigHealthIndicator indicator = context.getBean(SpringCloudConfigHealthIndicator)

        when:
        HealthResult first = Mono.from(indicator.result).block()
        configServer.stop()
        HealthResult second = Mono.from(indicator.result).block()

        then:
        first.status == HealthStatus.UP
        second.status == secondStatus

        cleanup:
        context?.close()
        configServer?.close()

        where:
        ttl   | secondStatus
        '5m'  | HealthStatus.UP
        '0s'  | HealthStatus.DOWN
    }

    void "an unreachable config server is reported as DOWN"() {
        given:
        ApplicationContext context = ApplicationContext.run(FAST_RETRY + [
                'micronaut.application.name': 'myapp',
                'spring.cloud.config.enabled': true,
                'spring.cloud.config.uri'    : 'http://localhost:1'
        ])

        when:
        HealthResult result = Mono.from(context.getBean(SpringCloudConfigHealthIndicator).result).block()

        then:
        result.status == HealthStatus.DOWN
        (result.details as Map).error

        cleanup:
        context.close()
    }

    void "the configured credentials are used for the health check"() {
        given:
        EmbeddedServer configServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigSecuredServer.ENABLED): true])
        ApplicationContext context = ApplicationContext.run(FAST_RETRY + [
                'micronaut.application.name' : 'myapp',
                'spring.cloud.config.enabled' : true,
                'spring.cloud.config.uri'     : configServer.URL.toString()
        ] + credentials)

        when:
        HealthResult result = Mono.from(context.getBean(SpringCloudConfigHealthIndicator).result).block()

        then:
        result.status == expectedStatus

        cleanup:
        context?.close()
        configServer?.close()

        where:
        credentials                                                                     | expectedStatus
        ['spring.cloud.config.username': 'user', 'spring.cloud.config.password': 'secured'] | HealthStatus.UP
        [:]                                                                             | HealthStatus.DOWN
    }

    void "the indicator is exposed through the health endpoint"() {
        given:
        EmbeddedServer configServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigServer.ENABLED): true])
        EmbeddedServer application = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'      : 'myapp',
                'spring.cloud.config.enabled'      : true,
                'spring.cloud.config.uri'          : configServer.URL.toString(),
                'endpoints.health.details-visible' : 'ANONYMOUS'
        ])
        HttpClient client = application.applicationContext.createBean(HttpClient, application.URL)

        when:
        Map body = client.toBlocking().retrieve(HttpRequest.GET('/health'), Map)

        then:
        body.details['spring-cloud-config'].status == 'UP'
        body.details['spring-cloud-config'].details.name == 'myapp'

        cleanup:
        client?.close()
        application?.close()
        configServer?.close()
    }

    void "the indicator is only present when the client is enabled and the health check is enabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(config)

        expect:
        context.containsBean(SpringCloudConfigHealthIndicator) == present

        cleanup:
        context.close()

        where:
        config                                                                             | present
        [:]                                                                                | false
        ['spring.cloud.config.enabled': true]                                              | true
        ['spring.cloud.config.enabled': true, 'spring.cloud.config.health-check': false]   | false
    }
}
