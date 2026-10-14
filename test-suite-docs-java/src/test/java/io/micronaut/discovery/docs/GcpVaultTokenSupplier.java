package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Value;
import io.micronaut.discovery.vault.config.auth.VaultAuthHttpClient;
import io.micronaut.discovery.vault.config.auth.VaultTokenSupplier;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
// end::imports[]

@Requires(property = "spec.name", value = "GcpVaultTokenSupplierTest")
// tag::class[]
@Singleton
@BootstrapContextCompatible // <1>
public class GcpVaultTokenSupplier implements VaultTokenSupplier {

    private final VaultAuthHttpClient authClient;
    private final Path jwtFile;

    public GcpVaultTokenSupplier(VaultAuthHttpClient authClient,
                                 @Value("${gcp.vault.jwt-file}") String jwtFile) {
        this.authClient = authClient;
        this.jwtFile = Path.of(jwtFile);
    }

    @Override
    public Publisher<String> getToken() {
        return Mono.fromCallable(() -> Files.readString(jwtFile).strip()) // <2>
            .flatMap(jwt -> Mono.from(authClient.login("gcp", Map.of("role", "my-app", "jwt", jwt)))) // <3>
            .map(response -> response.auth().clientToken());
    }
}
// end::class[]
