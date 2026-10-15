package io.micronaut.discovery.vault

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.Container
import org.testcontainers.vault.VaultContainer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class VaultMicronautSerializationIntegrationTest extends Specification {
    @Shared @AutoCleanup VaultContainer vaultContainer = new VaultContainer("vault:1.2.3")
        .withSecretInVault("secret/myapp",
                "micronaut.security.oauth2.clients.test.client-id=hello",
                "micronaut.security.oauth2.clients.test.client-secret=world"
        )
        .withVaultToken("testtoken")

    void "test list secrets from vault"() {
        given:
        vaultContainer.start()
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")
        ApplicationContext context = ApplicationContext.run([
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "vault.client.config.enabled": true,
                "vault.client.kv-version": "V2",
                "vault.client.token": "testtoken",
                "vault.client.uri": vaultContainer.getHttpHostAddress()
        ])

        expect:
        context.getRequiredProperty("micronaut.security.oauth2.clients.test.client-id", String) == 'hello'
    }

    void "test AppRole login response is deserialized with Micronaut Serialization"() {
        given:
        vaultContainer.start()
        vault('auth enable approle')
        vault('policy write read-secrets - <<\'EOF\'\npath "secret/*" { capabilities = ["read"] }\nEOF')
        vault('write auth/approle/role/micronaut token_policies=read-secrets')
        String roleId = vault('read -field=role_id auth/approle/role/micronaut/role-id')
        String secretId = vault('write -f -field=secret_id auth/approle/role/micronaut/secret-id')
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")
        ApplicationContext context = ApplicationContext.run([
                "micronaut.application.name": "myapp",
                "micronaut.config-client.enabled": true,
                "vault.client.config.enabled": true,
                "vault.client.kv-version": "V2",
                "vault.client.authentication": "APPROLE",
                "vault.client.app-role.role-id": roleId,
                "vault.client.app-role.secret-id": secretId,
                "vault.client.uri": vaultContainer.getHttpHostAddress()
        ])

        expect:
        context.getRequiredProperty("micronaut.security.oauth2.clients.test.client-secret", String) == 'world'

        cleanup:
        context?.close()
    }

    private String vault(String command) {
        Container.ExecResult result = vaultContainer.execInContainer('/bin/sh', '-c',
                "VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=testtoken vault ${command}".toString())
        assert result.exitCode == 0: "vault ${command} failed: ${result.stderr}"
        result.stdout.trim()
    }


}
