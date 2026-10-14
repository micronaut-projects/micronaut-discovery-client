package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.discovery.vault.config.auth.VaultTokenSupplier
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification

@Property(name = "spec.name", value = "GcpVaultTokenSupplierSpec")
@Property(name = "vault.client.uri", value = 'http://localhost:${micronaut.server.port}')
@Property(name = "vault.client.config.enabled", value = "true")
@Property(name = "gcp.vault.jwt-file", value = '${java.io.tmpdir}/gcp-vault-token-supplier-groovy.jwt')
@MicronautTest
class GcpVaultTokenSupplierSpec extends Specification {

    @Inject
    VaultTokenSupplier tokenSupplier

    @Value('${gcp.vault.jwt-file}')
    String jwtFile

    void "the custom supplier logs in with the GCP auth method"() {
        given:
        new File(jwtFile).text = "signed-jwt\n"

        expect:
        tokenSupplier instanceof GcpVaultTokenSupplier
        Mono.from(tokenSupplier.token).block() == "my-app:signed-jwt"
    }
}
