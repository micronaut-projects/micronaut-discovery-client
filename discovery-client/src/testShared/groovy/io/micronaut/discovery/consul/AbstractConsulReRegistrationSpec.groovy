/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.consul

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.ConsulServiceEntry
import io.micronaut.discovery.consul.registration.ConsulAutoRegistration
import io.micronaut.discovery.registration.AutoRegistration
import io.micronaut.health.CurrentHealthStatus
import io.micronaut.health.HealthStatus
import io.micronaut.health.HeartbeatTask
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ReactiveClientResultTransformer
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Requires
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verifies that a service re-registers with Consul after Consul lost the registration,
 * for example because the Consul agent was restarted. Consul 1.9 answers a heartbeat for an
 * unknown check with a 500, newer versions answer with a 404. The Micronaut Reactor HTTP
 * client turns a 404 response into an empty publisher, so subclasses run with and without it.
 * Also verifies the de-registration that follows a failed fail-fast registration.
 */
@Requires({ DockerClientFactory.instance().isDockerAvailable() })
abstract class AbstractConsulReRegistrationSpec extends Specification {

    private static final String SERVICE_NAME = 'test-re-registration'

    /**
     * @return The number of {@link ReactiveClientResultTransformer} beans expected on the test classpath
     */
    abstract int expectedResultTransformers()

    void 'the expected HTTP client result transformers are present'() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBeansOfType(ReactiveClientResultTransformer).size() == expectedResultTransformers()

        cleanup:
        ctx.close()
    }

    void 'the service re-registers when Consul lost the registration and a #status heartbeat is reported (#image)'() {
        given:
        GenericContainer consul = new GenericContainer(image)
                .withExposedPorts(8500)
                .waitingFor(Wait.forHttp('/v1/status/leader').forPort(8500).forStatusCode(200))
        consul.start()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'            : SERVICE_NAME,
                'consul.client.host'                    : consul.host,
                'consul.client.port'                    : consul.getMappedPort(8500),
                'consul.client.registration.retry-count': 1,
                'consul.client.registration.retry-delay': '100ms',
                'micronaut.heartbeat.initial-delay'     : '1h',
                'micronaut.heartbeat.interval'          : '1h',
                'micronaut.health.monitor.enabled'      : false,
        ])
        ConsulClient consulClient = server.applicationContext.getBean(ConsulClient)
        String serviceId = "$SERVICE_NAME:$server.port"
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.2)

        expect: 'the service is registered'
        conditions.eventually {
            assert registeredServices(consulClient).containsKey(serviceId)
            // the application has processed the registration response, so it reports heartbeats
            assert server.applicationContext.getBean(ConsulAutoRegistration).@registered.get()
        }

        when: 'Consul loses the registration'
        Flux.from(consulClient.deregister(serviceId)).blockFirst()

        then:
        !registeredServices(consulClient).containsKey(serviceId)

        when: 'a heartbeat is reported'
        server.applicationContext.getBean(CurrentHealthStatus).update(status)
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then: 'the service is registered again'
        conditions.eventually {
            assert registeredServices(consulClient).containsKey(serviceId)
        }

        and: 'the recreated check is in the reported state'
        conditions.eventually {
            assert checkStatus(consul, "service:$serviceId") == expectedCheckStatus
        }

        and: 'the check of the new registration accepts heartbeats'
        Mono.from(consulClient.pass("service:$serviceId")).block() != null

        cleanup:
        server?.close()
        consul?.stop()

        where:
        [status, image] << [[HealthStatus.UP, HealthStatus.DOWN], ['consul:1.9.0', 'hashicorp/consul:1.20']].combinations()
        expectedCheckStatus = status == HealthStatus.UP ? 'passing' : 'critical'
    }

    void 'the service de-registers without blocking the event loop when a fail-fast registration fails'() {
        given:
        GenericContainer consul = new GenericContainer('hashicorp/consul:1.20')
                .withExposedPorts(8500)
                .waitingFor(Wait.forHttp('/v1/status/leader').forPort(8500).forStatusCode(200))
        consul.start()
        List<ILoggingEvent> events = new CopyOnWriteArrayList<>()
        AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                events << event
            }
        }
        appender.start()
        Logger logger = (Logger) LoggerFactory.getLogger(AutoRegistration)
        logger.addAppender(appender)
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.2)

        when: 'Consul rejects the registration because a metadata key exceeds its length limit'
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'            : SERVICE_NAME,
                'consul.client.host'                    : consul.host,
                'consul.client.port'                    : consul.getMappedPort(8500),
                'consul.client.registration.fail-fast'  : true,
                'consul.client.registration.retry-count': 1,
                'consul.client.registration.retry-delay': '100ms',
                'consul.client.registration.meta'       : [('k' * 129): 'value'],
                'micronaut.heartbeat.initial-delay'     : '1h',
                'micronaut.heartbeat.interval'          : '1h',
                'micronaut.health.monitor.enabled'      : false,
        ])

        then: 'the failed registration stops the server, which de-registers the service'
        conditions.eventually {
            assert !server.running
            // the context stops right after the server, so the de-registration may not reach Consul
            assert events.any { it.formattedMessage.contains('De-registered service') || it.formattedMessage.contains('Error occurred de-registering service') }
        }
        events.any { it.formattedMessage.contains('Error occurred during service registration with Consul') }
        !events.any { it.formattedMessage.contains('blocking') }

        cleanup:
        logger.detachAppender(appender)
        server?.close()
        consul?.stop()
    }

    private static Map<String, ConsulServiceEntry> registeredServices(ConsulClient consulClient) {
        Mono.from(consulClient.findServices()).block()
    }

    private static String checkStatus(GenericContainer consul, String checkId) {
        HttpClient client = HttpClient.create(new URL("http://${consul.host}:${consul.getMappedPort(8500)}"))
        try {
            Map<String, Map<String, Object>> checks = client.toBlocking().retrieve('/v1/agent/checks', Map)
            return checks[checkId]?.Status
        } finally {
            client.close()
        }
    }
}
