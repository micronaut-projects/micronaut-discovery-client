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
import io.micronaut.context.env.Environment
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.watch.Watcher
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class ConsulPropertySourceImporterIntegrationSpec extends Specification {

    @Shared
    @AutoCleanup
    GenericContainer consulContainer = new GenericContainer("consul:1.9.0").withExposedPorts(8500)

    @Shared
    @AutoCleanup
    EmbeddedServer writerServer

    @Shared
    ConsulClient consulClient

    def setupSpec() {
        consulContainer.start()
        writerServer = ApplicationContext.run(EmbeddedServer, [
            'consul.client.host': consulContainer.containerIpAddress,
            'consul.client.port': consulContainer.getMappedPort(8500)
        ], Environment.TEST)
        consulClient = writerServer.applicationContext.getBean(ConsulClient)
    }

    void 'consul importer resolves explicit namespace against real container'() {
        given:
        Flux.from(consulClient.putValue('/config/application/message', 'hello-from-consul')).blockFirst()

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "consul://${consulContainer.containerIpAddress}:${consulContainer.getMappedPort(8500)}/config/application"
        ], Environment.TEST)

        then:
        context.getRequiredProperty('message', String) == 'hello-from-consul'

        cleanup:
        context.close()
    }

    void 'consul importer watch true refreshes explicit path against real container'() {
        given:
        Flux.from(consulClient.putValue('/config/application/message', 'hello-from-consul')).blockFirst()

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.application.name': 'myapp',
            'micronaut.config.import': "consul://${consulContainer.containerIpAddress}:${consulContainer.getMappedPort(8500)}/config/application?watch=true",
            'consul.client.read-timeout': '5s',
            'consul.client.blocking-queries.delay-duration': '10ms'
        ], Environment.TEST)

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.5)

        then:
        context.getRequiredProperty('message', String) == 'hello-from-consul'
        conditions.eventually {
            context.getBean(Watcher).isWatching()
        }

        when:
        Flux.from(consulClient.putValue('/config/application/message', 'goodbye-from-consul')).blockFirst()

        then:
        conditions.eventually {
            context.getRequiredProperty('message', String) == 'goodbye-from-consul'
        }


        cleanup:
        context.close()
    }
}
