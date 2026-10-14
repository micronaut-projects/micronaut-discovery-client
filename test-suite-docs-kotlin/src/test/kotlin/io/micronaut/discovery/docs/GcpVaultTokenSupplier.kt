package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Value
import io.micronaut.discovery.vault.config.auth.VaultAuthHttpClient
import io.micronaut.discovery.vault.config.auth.VaultTokenSupplier
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.io.File
// end::imports[]

@Requires(property = "spec.name", value = "GcpVaultTokenSupplierTest")
// tag::class[]
@Singleton
@BootstrapContextCompatible // <1>
class GcpVaultTokenSupplier(
    private val authClient: VaultAuthHttpClient,
    @Value("\${gcp.vault.jwt-file}") private val jwtFile: String
) : VaultTokenSupplier {

    override fun getToken(): Publisher<String> =
        Mono.fromCallable { File(jwtFile).readText().trim() } // <2>
            .flatMap { jwt -> Mono.from(authClient.login("gcp", mapOf("role" to "my-app", "jwt" to jwt))) } // <3>
            .mapNotNull { response -> response.auth()?.clientToken() }
}
// end::class[]
