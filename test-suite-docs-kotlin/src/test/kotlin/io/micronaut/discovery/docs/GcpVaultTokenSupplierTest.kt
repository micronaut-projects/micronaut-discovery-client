package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.discovery.vault.config.auth.VaultTokenSupplier
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.io.File

@Property(name = "spec.name", value = "GcpVaultTokenSupplierTest")
@Property(name = "vault.client.uri", value = "http://localhost:\${micronaut.server.port}")
@Property(name = "vault.client.config.enabled", value = "true")
@Property(name = "gcp.vault.jwt-file", value = "\${java.io.tmpdir}/gcp-vault-token-supplier-kotlin.jwt")
@MicronautTest
class GcpVaultTokenSupplierTest {

    @Inject
    lateinit var tokenSupplier: VaultTokenSupplier

    @Value("\${gcp.vault.jwt-file}")
    lateinit var jwtFile: String

    @Test
    fun theCustomSupplierLogsInWithTheGcpAuthMethod() {
        File(jwtFile).writeText("signed-jwt\n")

        assertInstanceOf(GcpVaultTokenSupplier::class.java, tokenSupplier)
        assertEquals("my-app:signed-jwt", Mono.from(tokenSupplier.getToken()).block())
    }
}
