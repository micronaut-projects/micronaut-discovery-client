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
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.discovery.vault.config.auth.AppRoleVaultTokenSupplier
import io.micronaut.discovery.vault.config.auth.KubernetesVaultTokenSupplier
import io.micronaut.discovery.vault.config.auth.StaticVaultTokenSupplier
import io.micronaut.discovery.vault.config.auth.VaultTokenSupplier
import io.micronaut.discovery.vault.imports.VaultPropertySourceImporter
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path

@RestoreSystemProperties
class VaultAuthenticationSpec extends Specification {

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

    void 'static token remains the default authentication method'() {
        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig() + [
            'vault.client.token': MockingVaultAuthController.STATIC_TOKEN
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-static-token'
        context.getBean(VaultTokenSupplier) instanceof StaticVaultTokenSupplier
        controller.logins.get() == 0

        cleanup:
        context?.close()
    }

    void 'legacy client logs in with Kubernetes auth using the service account token file'() {
        given:
        Path jwtFile = writeJwt()

        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig() + [
            'vault.client.authentication': 'KUBERNETES',
            'vault.client.kubernetes.role': MockingVaultAuthController.KUBERNETES_ROLE,
            'vault.client.kubernetes.service-account-token-file': jwtFile.toString()
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-k8s-client-token'
        context.getRequiredProperty('myapp-secret', String) == 'present'
        context.getBean(VaultTokenSupplier) instanceof KubernetesVaultTokenSupplier
        controller.logins.get() == 1

        cleanup:
        context?.close()
    }

    void 'legacy client logs in with AppRole auth on a custom mount path'() {
        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig() + [
            'vault.client.authentication': 'approle',
            'vault.client.app-role.role-id': MockingVaultAuthController.ROLE_ID,
            'vault.client.app-role.secret-id': MockingVaultAuthController.SECRET_ID,
            'vault.client.app-role.path': 'custom-approle'
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-approle-client-token'
        context.getBean(VaultTokenSupplier) instanceof AppRoleVaultTokenSupplier
        controller.logins.get() == 1

        cleanup:
        context?.close()
    }

    void 'a user supplied VaultTokenSupplier bean replaces the built-in authentication'() {
        when:
        ApplicationContext context = ApplicationContext.run(legacyConfig() + [
            (CustomVaultTokenSupplier.ENABLED): true,
            'vault.client.token': 'ignored'
        ])

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-custom-token'
        context.getBean(VaultTokenSupplier) instanceof CustomVaultTokenSupplier

        cleanup:
        context?.close()
    }

    void 'legacy client fails when the Vault login is rejected'() {
        when:
        ApplicationContext.run(legacyConfig() + [
            'vault.client.authentication': 'APPROLE',
            'vault.client.app-role.role-id': MockingVaultAuthController.ROLE_ID,
            'vault.client.app-role.secret-id': 'wrong'
        ])

        then:
        ConfigurationException e = thrown()
        e.message.contains('Vault AppRole login')
    }

    void 'Kubernetes auth requires a role'() {
        given:
        Path jwtFile = writeJwt()

        when:
        ApplicationContext.run(legacyConfig() + [
            'vault.client.authentication': 'KUBERNETES',
            'vault.client.kubernetes.service-account-token-file': jwtFile.toString()
        ])

        then:
        ConfigurationException e = thrown()
        e.message.contains('vault.client.kubernetes.role')
    }

    void 'vault import logs in with AppRole options from the import URI'() {
        when: 'the bootstrap context is disabled so that the import is resolved once'
        ApplicationContext context = ApplicationContext.builder()
            .bootstrapEnvironment(false)
            .properties('micronaut.config.import': "vault://localhost:${vaultServer.port}/application?kv-version=V2&secret-engine-name=${MockingVaultAuthController.ENGINE}" +
                "&authentication=approle&role-id=${MockingVaultAuthController.ROLE_ID}&secret-id=${MockingVaultAuthController.SECRET_ID}")
            .start()

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-approle-client-token'
        controller.logins.get() == 1

        cleanup:
        context?.close()
    }

    void 'vault import logs in with Kubernetes options from the import URI'() {
        given:
        Path jwtFile = writeJwt()

        when: 'the bootstrap context is disabled so that the import is resolved once'
        ApplicationContext context = ApplicationContext.builder()
            .bootstrapEnvironment(false)
            .properties('micronaut.config.import': "vault://localhost:${vaultServer.port}/application?kv-version=V2&secret-engine-name=${MockingVaultAuthController.ENGINE}" +
                "&authentication=kubernetes&kubernetes-role=${MockingVaultAuthController.KUBERNETES_ROLE}&kubernetes-path=k8s/cluster-a" +
                "&service-account-token-file=${jwtFile}")
            .start()

        then:
        context.getRequiredProperty('auth-secret', String) == 'loaded-with-k8s-client-token'
        controller.logins.get() == 1

        cleanup:
        context?.close()
    }

    void 'structured vault import declarations accept authentication options'() {
        when:
        def declaration = new VaultPropertySourceImporter().newImportDeclaration(ConvertibleValues.of([
            uri                         : 'http://localhost:8200',
            path                        : 'application',
            authentication              : 'kubernetes',
            'role-id'                   : 'role',
            'secret-id'                 : 'secret',
            'app-role-path'             : 'custom-approle',
            'kubernetes-role'           : 'k8s-role',
            'kubernetes-path'           : 'k8s/cluster-a',
            'service-account-token-file': '/tmp/token'
        ]), null)

        then:
        declaration.properties() == [
            'vault.client.uri'                                  : 'http://localhost:8200',
            'vault.client.authentication'                       : 'kubernetes',
            'vault.client.app-role.role-id'                     : 'role',
            'vault.client.app-role.secret-id'                   : 'secret',
            'vault.client.app-role.path'                        : 'custom-approle',
            'vault.client.kubernetes.role'                      : 'k8s-role',
            'vault.client.kubernetes.path'                      : 'k8s/cluster-a',
            'vault.client.kubernetes.service-account-token-file': '/tmp/token'
        ]
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

    private Path writeJwt() {
        Path jwtFile = tempDir.resolve('token')
        Files.writeString(jwtFile, MockingVaultAuthController.KUBERNETES_JWT + '\n')
        jwtFile
    }
}
