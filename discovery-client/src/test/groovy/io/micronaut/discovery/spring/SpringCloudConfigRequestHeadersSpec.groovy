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

import java.nio.charset.StandardCharsets

/**
 * Verifies custom headers, the {@code X-Config-Token} header and bearer authorization
 * are sent to the Spring Cloud Config Server by both the legacy client and the {@code springcloud://} importer.
 */
@RestoreSystemProperties
class SpringCloudConfigRequestHeadersSpec extends Specification {

    private static final String BASIC_USER_SECURED = 'Basic ' + Base64.encoder.encodeToString('user:secured'.getBytes(StandardCharsets.UTF_8))

    @Shared
    @AutoCleanup
    EmbeddedServer configServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigHeaderEchoServer.ENABLED): true])

    void setup() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')
    }

    void 'legacy client sends configured headers, config token and bearer token'() {
        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig([
            'spring.cloud.config.headers.X-Foo': 'foo',
            'spring.cloud.config.headers.X-Bar': 'bar',
            'spring.cloud.config.token': 'vault-token',
            'spring.cloud.config.bearer-token': 'oauth-token'
        ]))

        then:
        context.getRequiredProperty('echo.application', String) == 'myapp'
        context.getRequiredProperty('echo.x-foo', String) == 'foo'
        context.getRequiredProperty('echo.x-bar', String) == 'bar'
        context.getRequiredProperty('echo.x-config-token', String) == 'vault-token'
        context.getRequiredProperty('echo.authorization', String) == 'Bearer oauth-token'

        cleanup:
        context?.close()
    }

    void 'legacy client sends configured headers together with basic authorization'() {
        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig([
            'spring.cloud.config.label': 'main',
            'spring.cloud.config.headers.X-Foo': 'foo',
            'spring.cloud.config.username': 'user',
            'spring.cloud.config.password': 'secured'
        ]))

        then:
        context.getRequiredProperty('echo.x-foo', String) == 'foo'
        context.getRequiredProperty('echo.authorization', String) == BASIC_USER_SECURED
        !context.containsProperty('echo.x-config-token')

        cleanup:
        context?.close()
    }

    void 'legacy client sends an authorization header configured through headers'() {
        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig([
            'spring.cloud.config.headers.Authorization': 'Custom abc'
        ]))

        then:
        context.getRequiredProperty('echo.authorization', String) == 'Custom abc'

        cleanup:
        context?.close()
    }

    void 'legacy client rejects ambiguous authorization configuration: #description'() {
        when:
        ApplicationContext.run(legacyConfig(config))

        then:
        ConfigurationException e = thrown()
        e.message.contains('Only one of')
        e.message.contains('spring.cloud.config.bearer-token')

        where:
        description                       | config
        'basic auth and bearer token'     | ['spring.cloud.config.username': 'user', 'spring.cloud.config.password': 'secured', 'spring.cloud.config.bearer-token': 'oauth-token']
        'basic auth and header'           | ['spring.cloud.config.username': 'user', 'spring.cloud.config.password': 'secured', 'spring.cloud.config.headers.Authorization': 'Bearer x']
        'bearer token and header'         | ['spring.cloud.config.bearer-token': 'oauth-token', 'spring.cloud.config.headers.authorization': 'Bearer x']
    }

    void 'springcloud importer sends headers, config token and bearer token from URI options'() {
        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "springcloud://localhost:${configServer.port}/myapp/default?token=vault-token&bearer-token=oauth-token&header.X-Foo=foo&header.X-Bar=bar"
        ])

        then:
        context.getRequiredProperty('echo.application', String) == 'myapp'
        context.getRequiredProperty('echo.x-foo', String) == 'foo'
        context.getRequiredProperty('echo.x-bar', String) == 'bar'
        context.getRequiredProperty('echo.x-config-token', String) == 'vault-token'
        context.getRequiredProperty('echo.authorization', String) == 'Bearer oauth-token'

        cleanup:
        context?.close()
    }

    void 'springcloud importer sends headers together with user-info basic authorization'() {
        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "springcloud://user:secured@localhost:${configServer.port}/myapp/default?label=main&header.X-Foo=foo"
        ])

        then:
        context.getRequiredProperty('echo.x-foo', String) == 'foo'
        context.getRequiredProperty('echo.authorization', String) == BASIC_USER_SECURED

        cleanup:
        context?.close()
    }

    void 'springcloud importer supports headers and tokens in structured import declarations'() {
        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': [[
                provider    : 'springcloud',
                uri         : configServer.URL.toString(),
                application : 'myapp',
                profiles    : 'default',
                token       : 'vault-token',
                'bearer-token': 'oauth-token',
                headers     : ['X-Foo': 'foo']
            ]]
        ])

        then:
        context.getRequiredProperty('echo.x-foo', String) == 'foo'
        context.getRequiredProperty('echo.x-config-token', String) == 'vault-token'
        context.getRequiredProperty('echo.authorization', String) == 'Bearer oauth-token'

        cleanup:
        context?.close()
    }

    void 'springcloud importer rejects basic authorization combined with a bearer token'() {
        when:
        ApplicationContext.run([
            'micronaut.config.import': "springcloud://user:secured@localhost:${configServer.port}/myapp/default?bearer-token=oauth-token"
        ])

        then:
        ConfigurationException e = thrown()
        e.message.contains('Only one of')
    }

    private Map<String, Object> legacyConfig(Map<String, Object> extra) {
        [
            'micronaut.application.name'     : 'myapp',
            'micronaut.config-client.enabled': true,
            'spring.cloud.config.enabled'    : true,
            'spring.cloud.config.uri'        : configServer.URL.toString()
        ] + extra
    }
}
