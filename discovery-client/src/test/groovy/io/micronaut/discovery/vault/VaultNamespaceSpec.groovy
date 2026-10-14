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
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path

@RestoreSystemProperties
class VaultNamespaceSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer vaultServer = ApplicationContext.run(EmbeddedServer, [(MockingVaultAuthController.ENABLED): true])

    @TempDir
    Path tempDir

    MockingVaultAuthController controller

    void setup() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')
        controller = vaultServer.applicationContext.getBean(MockingVaultAuthController)
        controller.logins.set(0)
        controller.namespaces.clear()
    }

    void 'no namespace header is sent by default'() {
        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig() + [
            'vault.client.token': MockingVaultAuthController.STATIC_TOKEN
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-static-token'
        !controller.namespaces.isEmpty()
        controller.namespaces.every { it == MockingVaultAuthController.NO_NAMESPACE }

        cleanup:
        context?.close()
    }

    void 'legacy client sends the namespace header on login and secret reads'() {
        given:
        Path jwtFile = tempDir.resolve('token')
        Files.writeString(jwtFile, MockingVaultAuthController.KUBERNETES_JWT)

        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig() + [
            'vault.client.namespace': 'team-a/dev',
            'vault.client.authentication': 'KUBERNETES',
            'vault.client.kubernetes.role': MockingVaultAuthController.KUBERNETES_ROLE,
            'vault.client.kubernetes.service-account-token-file': jwtFile.toString()
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-k8s-client-token'
        controller.logins.get() == 1
        controller.namespaces.size() > 1
        controller.namespaces.every { it == 'team-a/dev' }

        cleanup:
        context?.close()
    }

    void 'the namespace header is sent when the Vault URI has a context path'() {
        given: 'a Vault behind a reverse proxy context path'
        EmbeddedServer proxiedVault = ApplicationContext.run(EmbeddedServer, [
            (MockingVaultAuthController.ENABLED): true,
            'micronaut.server.context-path'     : '/vault'
        ])
        MockingVaultAuthController proxiedController = proxiedVault.applicationContext.getBean(MockingVaultAuthController)

        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig() + [
            'vault.client.uri'      : "${proxiedVault.URL}/vault".toString(),
            'vault.client.token'    : MockingVaultAuthController.STATIC_TOKEN,
            'vault.client.namespace': 'team-c'
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-static-token'
        !proxiedController.namespaces.isEmpty()
        proxiedController.namespaces.every { it == 'team-c' }

        cleanup:
        context?.close()
        proxiedVault?.close()
    }

    void 'vault import sends the namespace from the import URI'() {
        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "vault://${MockingVaultAuthController.STATIC_TOKEN}@localhost:${vaultServer.port}/application" +
                "?kv-version=V2&secret-engine-name=${MockingVaultAuthController.ENGINE}&namespace=team-b"
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-static-token'
        !controller.namespaces.isEmpty()
        controller.namespaces.every { it == 'team-b' }

        cleanup:
        context?.close()
    }

    private Map<String, Object> legacyConfig() {
        [
            'micronaut.application.name'     : 'myapp',
            'micronaut.config-client.enabled': true,
            'vault.client.config.enabled'    : true,
            'vault.client.kv-version'        : 'V2',
            'vault.client.secret-engine-name': MockingVaultAuthController.ENGINE,
            'vault.client.uri'               : vaultServer.URL.toString()
        ]
    }
}
