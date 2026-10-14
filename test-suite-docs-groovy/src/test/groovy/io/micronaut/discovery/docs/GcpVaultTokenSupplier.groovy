package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Value
import io.micronaut.discovery.vault.config.auth.VaultAuthHttpClient
import io.micronaut.discovery.vault.config.auth.VaultLoginResponse
import io.micronaut.discovery.vault.config.auth.VaultTokenSupplier
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
// end::imports[]

@Requires(property = "spec.name", value = "GcpVaultTokenSupplierSpec")
// tag::class[]
@Singleton
@BootstrapContextCompatible // <1>
class GcpVaultTokenSupplier implements VaultTokenSupplier {

    private final VaultAuthHttpClient authClient
    private final File jwtFile

    GcpVaultTokenSupplier(VaultAuthHttpClient authClient,
                          @Value('${gcp.vault.jwt-file}') String jwtFile) {
        this.authClient = authClient
        this.jwtFile = new File(jwtFile)
    }

    @Override
    Publisher<String> getToken() {
        Mono.fromCallable { jwtFile.text.trim() } // <2>
            .flatMap { String jwt -> Mono.from(authClient.login('gcp', [role: 'my-app', jwt: jwt])) } // <3>
            .map { VaultLoginResponse response -> response.auth().clientToken() }
    }
}
// end::class[]
