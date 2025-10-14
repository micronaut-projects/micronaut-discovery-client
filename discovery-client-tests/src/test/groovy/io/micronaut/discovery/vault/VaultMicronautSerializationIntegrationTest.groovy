package io.micronaut.discovery.vault

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import org.testcontainers.DockerClientFactory
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


}
