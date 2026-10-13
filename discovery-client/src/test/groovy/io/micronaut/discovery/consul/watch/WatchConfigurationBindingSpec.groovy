package io.micronaut.discovery.consul.watch

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import org.slf4j.event.Level
import spock.lang.Specification

class WatchConfigurationBindingSpec extends Specification {

    void "test watch configuration defaults"() {
        when:
        def configuration = new WatchConfiguration()

        then:
        !configuration.isEnabled()
        configuration.getMaxRetryAttempts() == WatchConfiguration.DEFAULT_MAX_RETRIES
        configuration.getRetryDelayMs() == WatchConfiguration.DEFAULT_RETRY_DELAY
        configuration.getKvNotFoundLogLevel() == Level.INFO
    }

    void "test watch retry and log level properties are bound"() {
        given:
        def context = ApplicationContext.run(
                'micronaut.application.name': 'watch-binding',
                'consul.client.host': 'localhost',
                'consul.client.port': SocketUtils.findAvailableTcpPort(),
                'consul.client.watch.enabled': true,
                'consul.client.watch.max-retry-attempts': 7,
                'consul.client.watch.retry-delay-ms': 1500,
                'consul.client.watch.kv-not-found-log-level': 'error'
        )

        when:
        def configuration = context.getBean(WatchConfiguration)

        then:
        configuration.isEnabled()
        configuration.getMaxRetryAttempts() == 7
        configuration.getRetryDelayMs() == 1500
        configuration.getKvNotFoundLogLevel() == Level.ERROR

        cleanup:
        context.close()
    }
}
