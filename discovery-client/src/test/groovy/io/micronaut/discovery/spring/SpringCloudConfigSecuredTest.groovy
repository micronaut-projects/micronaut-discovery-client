/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.discovery.vault.MockingVaultServerV1Controller
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Tests for getting properties from the secured cloud config server.
 */
@RestoreSystemProperties
class SpringCloudConfigSecuredTest extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [(MockSpringCloudConfigSecuredServer.ENABLED): true])

    void "test spring name configuration field when server secured and authorization provided"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")
        ApplicationContext context = ApplicationContext.run([
                (MockSpringCloudConfigServer.ENABLED): true,
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "spring.cloud.config.enabled": true,
                "spring.cloud.config.name": "myapp-from-spring",
                "spring.cloud.config.uri": embeddedServer.getURL().toString(),
                "spring.cloud.config.username": "user",
                "spring.cloud.config.password": "secured"
        ], "first", "second")

        expect:
        "myapp-from-spring" == context.getRequiredProperty("app-name-from-spring-config", String.class)

        cleanup:
        context.stop()
    }

    void "test when cloud server secured and authorization not provided"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")

        when:
        ApplicationContext context = ApplicationContext.run([
                (MockSpringCloudConfigServer.ENABLED): true,
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "spring.cloud.config.enabled": true,
                "spring.cloud.config.name": "myapp-from-spring",
                "spring.cloud.config.uri": embeddedServer.getURL().toString()
        ], "first", "second")

        then:
        def e = thrown(ConfigurationException)
        e.cause instanceof HttpClientResponseException

        cleanup:
        if (context != null) {
            context.stop()
        }
    }

    void "test when cloud server secured and only username is provided"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")

        when:
        ApplicationContext context = ApplicationContext.run([
                (MockSpringCloudConfigServer.ENABLED): true,
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "spring.cloud.config.enabled": true,
                "spring.cloud.config.name": "myapp-from-spring",
                "spring.cloud.config.uri": embeddedServer.getURL().toString(),
                "spring.cloud.config.username": "user",
        ], "first", "second")

        then:
        def e = thrown(ConfigurationException)
        e.cause instanceof HttpClientResponseException

        cleanup:
        if (context != null) {
            context.stop()
        }
    }

    void "test when cloud server secured and invalid user credentials provided"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")

        when:
        ApplicationContext context = ApplicationContext.run([
                (MockSpringCloudConfigServer.ENABLED): true,
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "spring.cloud.config.enabled": true,
                "spring.cloud.config.name": "myapp-from-spring",
                "spring.cloud.config.uri": embeddedServer.getURL().toString(),
                "spring.cloud.config.username": "user",
                "spring.cloud.config.password": "nonsecured"
        ], "first", "second")

        then:
        def e = thrown(ConfigurationException)
        e.cause instanceof HttpClientResponseException

        cleanup:
        if (context != null) {
            context.stop()
        }
    }

}
