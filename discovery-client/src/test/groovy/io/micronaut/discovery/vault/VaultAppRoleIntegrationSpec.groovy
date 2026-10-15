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
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.Container
import org.testcontainers.vault.VaultContainer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
@RestoreSystemProperties
class VaultAppRoleIntegrationSpec extends Specification {

    private static final String ROOT_TOKEN = 'testtoken'

    @Shared
    @AutoCleanup
    VaultContainer vaultContainer = new VaultContainer("vault:1.2.3")
        .withSecretInVault("secret/application", "approle.secret=hello-from-approle")
        .withSecretInVault("secret/myapp", "approle.app-secret=hello-myapp")
        .withVaultToken(ROOT_TOKEN)

    @Shared
    String roleId

    @Shared
    String secretId

    void setupSpec() {
        vaultContainer.start()
        vault('auth enable approle')
        vault('policy write read-secrets - <<\'EOF\'\npath "secret/*" { capabilities = ["read"] }\nEOF')
        vault('write auth/approle/role/micronaut token_policies=read-secrets token_ttl=10m')
        roleId = vault('read -field=role_id auth/approle/role/micronaut/role-id')
        secretId = vault('write -f -field=secret_id auth/approle/role/micronaut/secret-id')
    }

    void setup() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')
    }

    void 'vault import authenticates with AppRole against a real Vault server'() {
        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "vault://${vaultContainer.host}:${vaultContainer.firstMappedPort}/application?kv-version=V2&secret-engine-name=secret" +
                "&authentication=approle&role-id=${roleId}&secret-id=${secretId}"
        ], Environment.TEST)

        then:
        context.getRequiredProperty('approle.secret', String) == 'hello-from-approle'

        cleanup:
        context?.close()
    }

    void 'legacy configuration client authenticates with AppRole against a real Vault server'() {
        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.application.name'   : 'myapp',
            'micronaut.config-client.enabled': true,
            'vault.client.config.enabled'  : true,
            'vault.client.kv-version'      : 'V2',
            'vault.client.uri'             : vaultContainer.httpHostAddress,
            'vault.client.authentication'  : 'APPROLE',
            'vault.client.app-role.role-id': roleId,
            'vault.client.app-role.secret-id': secretId
        ], Environment.TEST)

        then:
        context.getRequiredProperty('approle.secret', String) == 'hello-from-approle'
        context.getRequiredProperty('approle.app-secret', String) == 'hello-myapp'

        cleanup:
        context?.close()
    }

    void 'vault import fails when the AppRole secret id is invalid'() {
        when:
        ApplicationContext.run([
            'micronaut.config.import': "vault://${vaultContainer.host}:${vaultContainer.firstMappedPort}/application?kv-version=V2&secret-engine-name=secret" +
                "&authentication=approle&role-id=${roleId}&secret-id=not-the-secret"
        ], Environment.TEST)

        then:
        ConfigurationException e = thrown()
        e.message.contains('Vault AppRole login')
    }

    private String vault(String command) {
        Container.ExecResult result = vaultContainer.execInContainer('/bin/sh', '-c',
            "VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=${ROOT_TOKEN} vault ${command}".toString())
        assert result.exitCode == 0: "vault ${command} failed: ${result.stderr}"
        result.stdout.trim()
    }
}
