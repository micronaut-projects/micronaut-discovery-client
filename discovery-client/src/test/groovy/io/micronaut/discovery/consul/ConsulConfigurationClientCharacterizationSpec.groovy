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
import io.micronaut.discovery.client.config.DistributedPropertySourceLocator
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.config.ConsulConfigurationClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class ConsulConfigurationClientCharacterizationSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED): true
    ])

    @AutoCleanup
    @Shared
    ApplicationContext consulClientContext = ApplicationContext.run([
            'consul.client.host': 'localhost',
            'consul.client.port': consulServer.port
    ])

    @Shared
    ConsulClient consulClient = consulClientContext.getBean(ConsulClient)

    def setup() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')
        consulServer.applicationContext.getBean(MockConsulServer).keyvalues.clear()
    }

    void 'legacy Consul exposes only the composite ConfigurationClient without the bootstrap locator until ConfigurationClient.ENABLED is set'() {
        when:
        ApplicationContext context = ApplicationContext.run([
                'consul.client.host': 'localhost',
                'consul.client.port': consulServer.port,
                'consul.client.config.enabled': true
        ])

        then:
        context.containsBean(ConfigurationClient)
        !context.containsBean(ConsulConfigurationClient)
        !context.containsBean(DistributedPropertySourceLocator)

        cleanup:
        context.close()
    }

    void 'legacy Consul native configuration preserves environment precedence'() {
        given:
        writeNativeValue('application', 'some.consul.value-1', '01')
        writeNativeValue('application', 'some.consul.value-2', '02')
        writeNativeValue('application', 'some.consul.value-3', '03')
        writeNativeValue('application', 'some.consul.value-4', '04')
        writeNativeValue('application', 'some.consul.value-5', '05')
        writeNativeValue('application', 'some.consul.value-6', '06')
        writeNativeValue('application,first', 'some.consul.value-1', '11')
        writeNativeValue('application,first', 'some.consul.value-2', '12')
        writeNativeValue('application,first', 'some.consul.value-3', '13')
        writeNativeValue('application,first', 'some.consul.value-4', '14')
        writeNativeValue('application,first', 'some.consul.value-5', '15')
        writeNativeValue('application,second', 'some.consul.value-1', '21')
        writeNativeValue('application,second', 'some.consul.value-2', '22')
        writeNativeValue('application,second', 'some.consul.value-3', '23')
        writeNativeValue('application,second', 'some.consul.value-4', '24')
        writeNativeValue('test-app', 'some.consul.value-1', '31')
        writeNativeValue('test-app', 'some.consul.value-2', '32')
        writeNativeValue('test-app', 'some.consul.value-3', '33')
        writeNativeValue('test-app,first', 'some.consul.value-1', '41')
        writeNativeValue('test-app,first', 'some.consul.value-2', '42')
        writeNativeValue('test-app,second', 'some.consul.value-1', '51')

        when:
        ApplicationContext context = ApplicationContext.run([
                (ConfigurationClient.ENABLED): true,
                'micronaut.application.name': 'test-app',
                'consul.client.host': 'localhost',
                'consul.client.port': consulServer.port
        ], 'first', 'second')

        then:
        context.environment.getRequiredProperty('some.consul.value-1', String) == '51'
        context.environment.getRequiredProperty('some.consul.value-2', String) == '42'
        context.environment.getRequiredProperty('some.consul.value-3', String) == '33'
        context.environment.getRequiredProperty('some.consul.value-4', String) == '24'
        context.environment.getRequiredProperty('some.consul.value-5', String) == '15'
        context.environment.getRequiredProperty('some.consul.value-6', String) == '06'

        cleanup:
        context.close()
    }

    void 'legacy Consul file configuration preserves custom path and application override precedence'() {
        given:
        writeFileValue('some-path/config', 'application.properties', '''
datasource.url=mysql://blah
datasource.driver=java.SomeDriver
''')
        writeFileValue('some-path/config', 'application-test.json', '{ "some": "value" }')
        writeFileValue('some-path/config', 'sample-service.yml', '''
datasource:
  url: mysql://blah
''')
        writeFileValue('some-path/config', 'sample-service-test.yml', '''
datasource:
  url: mysql://overridden
''')

        when:
        ApplicationContext context = ApplicationContext.run([
                (ConfigurationClient.ENABLED): true,
                'consul.client.config.path': 'some-path/config',
                'consul.client.config.format': 'file',
                'micronaut.application.name': 'sample-service',
                'consul.client.host': 'localhost',
                'consul.client.port': consulServer.port
        ], 'test')

        then:
        context.environment.getRequiredProperty('some', String) == 'value'
        context.environment.getRequiredProperty('datasource.url', String) == 'mysql://overridden'
        context.environment.getRequiredProperty('datasource.driver', String) == 'java.SomeDriver'

        cleanup:
        context.close()
    }

    private void writeNativeValue(String env, String name, String value) {
        Flux.from(consulClient.putValue("/config/${env}/${name}", value)).blockFirst()
    }

    private void writeFileValue(String root, String name, String value) {
        Flux.from(consulClient.putValue("${root}/${name}", value)).blockFirst()
    }
}
