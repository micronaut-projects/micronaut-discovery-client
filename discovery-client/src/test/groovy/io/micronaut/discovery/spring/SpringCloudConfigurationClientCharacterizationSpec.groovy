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
package io.micronaut.discovery.spring

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.discovery.client.config.DistributedPropertySourceLocator
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.spring.config.SpringCloudConfigurationClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class SpringCloudConfigurationClientCharacterizationSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer configServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigServer.ENABLED): true])

    @Shared
    @AutoCleanup
    EmbeddedServer securedConfigServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigSecuredServer.ENABLED): true])

    void 'legacy Spring Cloud exposes only the composite ConfigurationClient without the bootstrap locator until ConfigurationClient.ENABLED is set'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
                'spring.cloud.config.enabled': true,
                'spring.cloud.config.uri': configServer.URL.toString()
        ])

        then:
        context.containsBean(ConfigurationClient)
        !context.containsBean(SpringCloudConfigurationClient)
        !context.containsBean(DistributedPropertySourceLocator)

        cleanup:
        context.close()
    }

    void 'legacy Spring Cloud configuration preserves property precedence for application name and spring-specific name override separately'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext applicationNameContext = ApplicationContext.run([
                (MockSpringCloudConfigServer.ENABLED): true,
                'micronaut.application.name': 'myapp',
                'micronaut.config-client.enabled': true,
                'spring.cloud.config.enabled': true,
                'spring.cloud.config.uri': configServer.URL.toString()
        ], 'first', 'second')
        ApplicationContext springNameContext = ApplicationContext.run([
                (MockSpringCloudConfigServer.ENABLED): true,
                'micronaut.application.name': 'myapp',
                'micronaut.config-client.enabled': true,
                'spring.cloud.config.enabled': true,
                'spring.cloud.config.name': 'myapp-from-spring',
                'spring.cloud.config.uri': configServer.URL.toString()
        ], 'first', 'second')

        then:
        applicationNameContext.containsBean(SpringCloudConfigurationClient)
        applicationNameContext.containsBean(DistributedPropertySourceLocator)
        applicationNameContext.getRequiredProperty('config-secret-1', Integer) == 1
        applicationNameContext.getRequiredProperty('config-secret-2', Integer) == 1
        applicationNameContext.getRequiredProperty('config-secret-3', Integer) == 1
        applicationNameContext.getRequiredProperty('config-secret-4', Integer) == 1
        applicationNameContext.getRequiredProperty('config-secret-5', Integer) == 1
        applicationNameContext.getRequiredProperty('config-secret-6', Integer) == 1
        springNameContext.getRequiredProperty('app-name-from-spring-config', String) == 'myapp-from-spring'

        cleanup:
        applicationNameContext.close()
        springNameContext.close()
    }

    void 'legacy Spring Cloud wraps secured-server authorization failures in ConfigurationException'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext.run([
                (MockSpringCloudConfigServer.ENABLED): true,
                'micronaut.application.name': 'myapp',
                'micronaut.config-client.enabled': true,
                'spring.cloud.config.enabled': true,
                'spring.cloud.config.name': 'myapp-from-spring',
                'spring.cloud.config.uri': securedConfigServer.URL.toString()
        ], 'first', 'second')

        then:
        ConfigurationException e = thrown()
        e.cause instanceof HttpClientResponseException
    }
}
