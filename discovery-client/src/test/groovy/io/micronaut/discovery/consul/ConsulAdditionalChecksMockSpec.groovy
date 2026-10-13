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
import io.micronaut.context.annotation.Requires
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulCheck
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry
import io.micronaut.discovery.consul.registration.ConsulCheckCustomizer
import io.micronaut.http.HttpMethod
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ConsulAdditionalChecksMockSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED): true
    ])

    void 'additional checks are registered next to the default TTL check'() {
        given:
        String serviceName = 'additional-checks'
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'                                        : serviceName,
                'consul.client.host'                                                : 'localhost',
                'consul.client.port'                                                : consulServer.port,
                'spec.check-customizer'                                             : true,
                'consul.client.registration.checks.database.tcp'                    : 'localhost:5432',
                'consul.client.registration.checks.database.interval'               : '5s',
                'consul.client.registration.checks.database.timeout'                : '1500ms',
                'consul.client.registration.checks.readiness.http'                  : '/health/readiness',
                'consul.client.registration.checks.readiness.method'                : 'POST',
                'consul.client.registration.checks.readiness.headers.X-Api-Key'     : ['secret'],
                'consul.client.registration.checks.readiness.tls-skip-verify'       : true,
                'consul.client.registration.checks.grpc.grpc'                       : 'localhost:9090',
                'consul.client.registration.checks.grpc.grpc-use-tls'               : true,
                'consul.client.registration.checks.grpc.id'                         : 'grpc-check',
                'consul.client.registration.checks.batch.ttl'                       : '1m',
                'consul.client.registration.checks.batch.status'                    : 'passing',
                'consul.client.registration.checks.batch.notes'                     : 'Updated by the batch job',
                'consul.client.registration.checks.batch.deregister-critical-service-after': '90m',
                'consul.client.registration.checks.external.http'                   : 'https://example.com/status',
                'consul.client.registration.checks.disabled.enabled'                : false,
                'consul.client.registration.checks.disabled.tcp'                    : 'localhost:1',
        ])
        String serviceId = "$serviceName:$server.port"
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect:
        conditions.eventually {
            assert MockConsulServer.newEntries.get(serviceName)
        }

        when:
        ConsulNewServiceEntry entry = MockConsulServer.newEntries.get(serviceName)
        Map<String, ConsulCheck> checks = entry.checks().collectEntries { [(it.id): it] }

        then: 'the default TTL check keeps the ID the heartbeat reports to'
        entry.checks()[0].id == "service:$serviceId"
        entry.checks()[0].ttl
        !entry.checks()[0].interval

        and: 'the configured checks are registered in name order, followed by the customized check'
        entry.checks()*.id == [
                "service:$serviceId",
                "service:$serviceId:batch",
                "service:$serviceId:database",
                "service:$serviceId:external",
                'grpc-check',
                "service:$serviceId:readiness",
                "custom-$serviceId",
        ]

        and:
        with(checks["service:$serviceId:database"]) {
            name == 'database'
            tcp == 'localhost:5432'
            interval == '5s'
            timeout == '1500ms'
            !http
            !ttl
            !status
        }
        with(checks["service:$serviceId:readiness"]) {
            http == new URL("http://localhost:$server.port/health/readiness")
            method == HttpMethod.POST
            header == ['X-Api-Key': ['secret']]
            tlsSkipVerify
            interval == '15s'
        }
        with(checks['grpc-check']) {
            name == 'grpc'
            grpc == 'localhost:9090'
            grpcUseTls
        }
        with(checks["service:$serviceId:batch"]) {
            ttl == '60s'
            !interval
            status == 'passing'
            notes == 'Updated by the batch job'
            deregisterCriticalServiceAfter == '5400s'
        }
        checks["service:$serviceId:external"].http == new URL('https://example.com/status')
        checks["custom-$serviceId"].tcp == 'localhost:6379'

        cleanup:
        server?.close()
    }

    void 'the default check keeps a configured ID when additional checks are registered'() {
        given:
        String serviceName = 'additional-checks-custom-id'
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'                    : serviceName,
                'consul.client.host'                            : 'localhost',
                'consul.client.port'                            : consulServer.port,
                'consul.client.registration.check.id'           : 'my-ttl-check',
                'consul.client.registration.checks.database.tcp': 'localhost:5432',
        ])
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect:
        conditions.eventually {
            assert MockConsulServer.newEntries.get(serviceName)
        }
        MockConsulServer.newEntries.get(serviceName).checks()*.id == ['my-ttl-check', "service:$serviceName:$server.port:database"]

        cleanup:
        server?.close()
    }

    void 'additional checks are registered when the default check is disabled'() {
        given:
        String serviceName = 'additional-checks-only'
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'                    : serviceName,
                'consul.client.host'                            : 'localhost',
                'consul.client.port'                            : consulServer.port,
                'consul.client.registration.check.enabled'      : false,
                'consul.client.registration.checks.database.tcp': 'localhost:5432',
        ])
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect:
        conditions.eventually {
            assert MockConsulServer.newEntries.get(serviceName)
        }
        MockConsulServer.newEntries.get(serviceName).checks()*.tcp == ['localhost:5432']

        cleanup:
        server?.close()
    }

    @Singleton
    @Requires(property = 'spec.check-customizer')
    static class TestCheckCustomizer implements ConsulCheckCustomizer {
        @Override
        void customize(ServiceInstance instance, String serviceId, List<ConsulCheck> checks) {
            ConsulCheck check = new ConsulCheck()
            check.id = "custom-$serviceId"
            check.name = 'redis'
            check.tcp = 'localhost:6379'
            check.interval = '10s'
            checks.add(check)
        }
    }
}
