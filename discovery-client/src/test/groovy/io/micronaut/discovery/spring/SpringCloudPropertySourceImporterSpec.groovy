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
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class SpringCloudPropertySourceImporterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer configServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigServer.ENABLED): true])

    @Shared
    @AutoCleanup
    EmbeddedServer securedConfigServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigSecuredServer.ENABLED): true])

    void 'springcloud importer resolves explicit application and profiles'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "springcloud://localhost:${configServer.port}/myapp/first,second"
        ])

        then:
        context.getRequiredProperty('config-secret-1', Integer) == 1
        context.getRequiredProperty('config-secret-6', Integer) == 1

        cleanup:
        context.close()
    }

    void 'springcloud importer resolves explicit spring config name path'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "springcloud://localhost:${configServer.port}/myapp-from-spring/default"
        ])

        then:
        context.getRequiredProperty('app-name-from-spring-config', String) == 'myapp-from-spring'

        cleanup:
        context.close()
    }

    void 'springcloud importer supports secured server credentials'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "springcloud://localhost:${securedConfigServer.port}/myapp-from-spring/default?username=user&password=secured"
        ])

        then:
        context.getRequiredProperty('app-name-from-spring-config', String) == 'myapp-from-spring'

        cleanup:
        context.close()
    }

    void 'springcloud importer fails for secured server without credentials'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext.run([
            'micronaut.config.import': "springcloud://localhost:${securedConfigServer.port}/myapp-from-spring/default"
        ])

        then:
        thrown(ConfigurationException)
    }
}
