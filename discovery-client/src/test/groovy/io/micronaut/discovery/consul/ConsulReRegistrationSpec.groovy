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

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.ConsulServiceEntry
import io.micronaut.health.CurrentHealthStatus
import io.micronaut.health.HealthStatus
import io.micronaut.health.HeartbeatTask
import io.micronaut.http.client.ReactiveClientResultTransformer
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Requires
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Verifies that a service re-registers with Consul after Consul lost the registration,
 * for example because the Consul agent was restarted. Consul 1.9 answers a heartbeat for an
 * unknown check with a 500, newer versions answer with a 404.
 */
@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class ConsulReRegistrationSpec extends Specification {

    private static final String SERVICE_NAME = 'test-re-registration'

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

        and: 'the check of the new registration accepts heartbeats'
        Mono.from(consulClient.pass("service:$serviceId")).block() != null

        cleanup:
        server?.close()
        consul?.stop()

        where:
        [status, image] << [[HealthStatus.UP, HealthStatus.DOWN], ['consul:1.9.0', 'hashicorp/consul:1.20']].combinations()
    }

    /**
     * @return The number of {@link ReactiveClientResultTransformer} beans expected on the test classpath
     */
    protected int expectedResultTransformers() {
        0
    }

    private static Map<String, ConsulServiceEntry> registeredServices(ConsulClient consulClient) {
        Mono.from(consulClient.findServices()).block()
    }
}
