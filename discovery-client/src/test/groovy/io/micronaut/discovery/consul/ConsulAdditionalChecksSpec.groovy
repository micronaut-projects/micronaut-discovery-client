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
import io.micronaut.health.CurrentHealthStatus
import io.micronaut.health.HealthStatus
import io.micronaut.health.HeartbeatTask
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Registers additional checks with a real Consul agent and verifies them through the agent API.
 */
@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class ConsulAdditionalChecksSpec extends Specification {

    @Shared
    @AutoCleanup('stop')
    GenericContainer consul = new GenericContainer('hashicorp/consul:1.20')
            .withExposedPorts(8500)
            .waitingFor(Wait.forHttp('/v1/status/leader').forPort(8500).forStatusCode(200))

    @Shared
    @AutoCleanup
    HttpClient consulHttpClient

    def setupSpec() {
        consul.start()
        consulHttpClient = HttpClient.create(new URL("http://$consul.host:${consul.getMappedPort(8500)}"))
    }

    void 'additional checks are registered with Consul and the TTL heartbeat keeps working (default check ID: #checkId)'() {
        given:
        String serviceName = 'test-additional-checks'
        Map<String, Object> config = [
                'micronaut.application.name'                         : serviceName,
                'consul.client.host'                                 : consul.host,
                'consul.client.port'                                 : consul.getMappedPort(8500),
                'micronaut.heartbeat.initial-delay'                  : '1h',
                'micronaut.heartbeat.interval'                       : '1h',
                'micronaut.health.monitor.enabled'                   : false,
                'spec.check-customizer'                              : true,
                // the checks run inside the container, where the Consul agent listens on port 8500
                'consul.client.registration.checks.agent-tcp.tcp'    : 'localhost:8500',
                'consul.client.registration.checks.agent-tcp.interval': '1s',
                'consul.client.registration.checks.agent-http.http'  : 'http://localhost:8500/v1/status/leader',
                'consul.client.registration.checks.agent-http.interval': '1s',
                'consul.client.registration.checks.agent-http.timeout': '2s',
                'consul.client.registration.checks.batch.ttl'        : '5m',
                'consul.client.registration.checks.batch.notes'      : 'Updated by the batch job',
        ]
        if (checkId) {
            config['consul.client.registration.check.id'] = checkId
        }
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config)
        String serviceId = "$serviceName:$server.port"
        String defaultCheckId = checkId ?: "service:$serviceId"
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        expect: 'all checks are registered for the service'
        conditions.eventually {
            Map<String, Map> checks = serviceChecks(serviceId)
            assert checks.keySet() == [
                    defaultCheckId,
                    "service:$serviceId:agent-http".toString(),
                    "service:$serviceId:agent-tcp".toString(),
                    "service:$serviceId:batch".toString(),
                    "custom-$serviceId".toString(),
            ] as Set
        }

        and: 'Consul runs the TCP and HTTP checks'
        conditions.eventually {
            Map<String, Map> checks = serviceChecks(serviceId)
            assert checks["service:$serviceId:agent-tcp".toString()].Type == 'tcp'
            assert checks["service:$serviceId:agent-tcp".toString()].Status == 'passing'
            assert checks["service:$serviceId:agent-http".toString()].Type == 'http'
            assert checks["service:$serviceId:agent-http".toString()].Status == 'passing'
            assert checks["custom-$serviceId".toString()].Type == 'tcp' // added by the customizer, nothing listens on its port
        }

        and: 'the additional TTL check is registered'
        with(serviceChecks(serviceId)["service:$serviceId:batch".toString()]) {
            Type == 'ttl'
            Name == 'batch'
            Notes == 'Updated by the batch job'
        }

        and: 'the default check is the TTL check'
        with(serviceChecks(serviceId)[defaultCheckId]) {
            Type == 'ttl'
            Status == 'passing'
        }

        when: 'a DOWN heartbeat is reported'
        server.applicationContext.getBean(CurrentHealthStatus).update(HealthStatus.DOWN)
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then: 'the default TTL check fails'
        conditions.eventually {
            assert serviceChecks(serviceId)[defaultCheckId].Status == 'critical'
        }

        when: 'an UP heartbeat is reported'
        server.applicationContext.getBean(CurrentHealthStatus).update(HealthStatus.UP)
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then: 'the default TTL check passes again and the additional checks are still registered'
        conditions.eventually {
            Map<String, Map> checks = serviceChecks(serviceId)
            assert checks[defaultCheckId].Status == 'passing'
            assert checks.size() == 5
        }

        cleanup:
        server?.close()

        where:
        checkId << [null, 'my-ttl-check']
    }

    private Map<String, Map> serviceChecks(String serviceId) {
        Map<String, Map> checks = consulHttpClient.toBlocking().retrieve('/v1/agent/checks', Map)
        checks.findAll { String id, Map check -> check.ServiceID == serviceId } as Map<String, Map>
    }
}
