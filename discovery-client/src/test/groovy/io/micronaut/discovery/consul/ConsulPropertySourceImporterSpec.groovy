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
import io.micronaut.discovery.consul.watch.Watcher
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConsulPropertySourceImporterSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
        (MockConsulServer.ENABLED): true
    ])

    @AutoCleanup
    @Shared
    ApplicationContext writerContext = ApplicationContext.run([
        'consul.client.host': 'localhost',
        'consul.client.port': consulServer.port
    ])

    @Shared
    ConsulClient consulClient = writerContext.getBean(ConsulClient)

    def setup() {
        consulServer.applicationContext.getBean(MockConsulServer).keyvalues.clear()
    }

    void 'consul importer resolves explicit native namespace without legacy config client enablement'() {
        given:
        writeValue('/config/application/message', 'hello')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "consul://localhost:${consulServer.port}/config/application"
        ])

        then:
        context.getRequiredProperty('message', String) == 'hello'
        !context.environment.getProperty('micronaut.config-client.enabled', Boolean).orElse(false)

        cleanup:
        context.close()
    }

    void 'consul importer resolves explicit json namespace'() {
        given:
        writeValue('/config/application', '{"datasource":{"url":"jdbc:mysql://localhost"}}')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "consul://localhost:${consulServer.port}/config/application?format=json"
        ])

        then:
        context.getRequiredProperty('datasource.url', String) == 'jdbc:mysql://localhost'

        cleanup:
        context.close()
    }

    void 'consul importer supports watch true refresh for explicit path'() {
        given:
        writeValue('/config/application/message', 'hello')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.application.name': 'myapp',
            'micronaut.config.import': "consul://localhost:${consulServer.port}/config/application?watch=true",
            'consul.client.read-timeout': '5s',
            'consul.client.blocking-queries.delay-duration': '10ms'
        ], Environment.TEST)

        then:
        context.getRequiredProperty('message', String) == 'hello'
        context.getRequiredProperty('micronaut.config.import.consul.watch-path', String) == 'config/application/'
        context.getRequiredProperty('micronaut.config.import.consul.watch-format', String) == 'NATIVE'
        long watcherTimeout = System.currentTimeMillis() + 5000
        while (!context.getBean(Watcher).isWatching() && System.currentTimeMillis() < watcherTimeout) {
            Thread.sleep(25)
        }
        context.getBean(Watcher).isWatching()

        when:
        writeValue('/config/application/message', 'goodbye')
        long timeout = System.currentTimeMillis() + 5000
        while (context.getProperty('message', String).orElse('') != 'goodbye' && System.currentTimeMillis() < timeout) {
            Thread.sleep(100)
        }

        then:
        context.getRequiredProperty('message', String) == 'goodbye'

        cleanup:
        context.close()
    }

    private void writeValue(String key, String value) {
        Flux.from(consulClient.putValue(key, value)).blockFirst()
    }
}
