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
package io.micronaut.discovery.vault

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class VaultImportLayoutSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer vaultServer = ApplicationContext.run(EmbeddedServer, [(MockingVaultServerV2Controller.ENABLED): true])

    void 'every key of the imported secret becomes a property and KV version 2 is the default'() {
        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "vault://testtoken@localhost:${vaultServer.port}/application/first?secret-engine-name=backendv2"
        ])

        then:
        context.getRequiredProperty('v2-secret-1', Integer) == 4
        context.getRequiredProperty('v2-secret-2', Integer) == 3
        context.getRequiredProperty('v2-secret-3', Integer) == 2
        context.getRequiredProperty('v2-secret-4', Integer) == 1

        cleanup:
        context?.close()
    }

    void 'the legacy vault client reads secrets when vault.client.uri has a scheme'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig("http://localhost:${vaultServer.port}"))

        then:
        context.getRequiredProperty('v2-secret-6', Integer) == 1

        cleanup:
        context?.close()
    }

    void 'the legacy vault client treats vault.client.uri without a scheme as a service id'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext.run(legacyConfig("localhost:${vaultServer.port}"))

        then:
        Exception e = thrown()
        fullMessage(e).contains("DiscoveryClientLoadBalancerFactory")
    }

    private static Map<String, Object> legacyConfig(String uri) {
        [
            'micronaut.application.name'     : 'vault-layout-app',
            'micronaut.config-client.enabled': true,
            'vault.client.config.enabled'    : true,
            'vault.client.token'             : 'testtoken',
            'vault.client.secret-engine-name': 'backendv2',
            'vault.client.uri'               : uri
        ]
    }

    private static String fullMessage(Throwable e) {
        StringBuilder message = new StringBuilder()
        for (Throwable t = e; t != null; t = t.cause) {
            message.append(t.message).append('\n')
        }
        message.toString()
    }
}
