package io.micronaut.discovery.vault.health

import io.micronaut.context.ApplicationContext
import io.micronaut.health.HealthStatus
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

class VaultHealthIndicatorSpec extends Specification {

    @Unroll
    void "Vault sys/health status #code is reported as #expectedStatus (#state)"() {
        given:
        EmbeddedServer vault = ApplicationContext.run(EmbeddedServer, [
                (MockVaultHealthServer.ENABLED): true,
                (MockVaultHealthServer.CODE)   : code
        ])
        ApplicationContext context = ApplicationContext.run(['vault.client.config.enabled': true, 'vault.client.uri': vault.URL.toString()])

        when:
        HealthResult result = Mono.from(context.getBean(VaultHealthIndicator).result).block()
        Map<String, Object> details = result.details as Map<String, Object>

        then:
        result.name == 'vault'
        result.status == expectedStatus
        details.state == state
        details.version == '1.17.2'
        details.sealed == sealed
        details.standby == standby
        details['performance-standby'] == (code == 473)
        details['replication-dr-mode'] == (code == 472 ? 'secondary' : 'disabled')
        details['cluster-name'] == 'vault-cluster-test'

        cleanup:
        context?.close()
        vault?.close()

        where:
        code | expectedStatus   | state                 | sealed | standby
        200  | HealthStatus.UP   | 'active'              | false  | false
        429  | HealthStatus.UP   | 'standby'             | false  | true
        472  | HealthStatus.UP   | 'dr-secondary'        | false  | false
        473  | HealthStatus.UP   | 'performance-standby' | false  | true
        501  | HealthStatus.DOWN | 'uninitialized'       | true   | false
        503  | HealthStatus.DOWN | 'sealed'              | true   | false
    }

    void "an unreachable Vault server is reported as DOWN"() {
        given:
        ApplicationContext context = ApplicationContext.run(['vault.client.config.enabled': true, 'vault.client.uri': 'http://localhost:1'])

        when:
        HealthResult result = Mono.from(context.getBean(VaultHealthIndicator).result).block()

        then:
        result.name == 'vault'
        result.status == HealthStatus.DOWN
        (result.details as Map).error

        cleanup:
        context.close()
    }

    void "the Vault health indicator is exposed through the health endpoint"() {
        given:
        EmbeddedServer vault = ApplicationContext.run(EmbeddedServer, [
                (MockVaultHealthServer.ENABLED): true,
                (MockVaultHealthServer.CODE)   : code
        ])
        EmbeddedServer application = ApplicationContext.run(EmbeddedServer, [
                'vault.client.config.enabled'      : true,
                'vault.client.uri'                 : vault.URL.toString(),
                'endpoints.health.details-visible' : 'ANONYMOUS'
        ])
        HttpClient client = application.applicationContext.createBean(HttpClient, application.URL)

        when:
        Map body
        try {
            body = client.toBlocking().retrieve(HttpRequest.GET('/health'), Map)
        } catch (HttpClientResponseException e) {
            body = e.response.getBody(Map).orElse(null)
        }

        then:
        body.details.vault.status == expectedStatus
        body.details.vault.details.version == '1.17.2'
        body.details.vault.details.state == state

        cleanup:
        client?.close()
        application?.close()
        vault?.close()

        where:
        code | expectedStatus | state
        429  | 'UP'           | 'standby'
        503  | 'DOWN'         | 'sealed'
    }

    void "the Vault health indicator is only present when Vault is configured and the health check is enabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(config)

        expect:
        context.containsBean(VaultHealthIndicator) == present

        cleanup:
        context.close()

        where:
        config                                                                         | present
        [:]                                                                            | false
        ['vault.client.uri': 'http://localhost:8200']                                                                    | false
        ['vault.client.config.enabled': true, 'vault.client.uri': 'http://localhost:8200']                                | true
        ['vault.client.config.enabled': true, 'vault.client.uri': 'http://localhost:8200', 'vault.client.health-check': false] | false
    }
}
