package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.discovery.vault.config.auth.VaultTokenSupplier;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Property(name = "spec.name", value = "GcpVaultTokenSupplierTest")
@Property(name = "vault.client.uri", value = "http://localhost:${micronaut.server.port}")
@Property(name = "vault.client.config.enabled", value = "true")
@Property(name = "gcp.vault.jwt-file", value = "${java.io.tmpdir}/gcp-vault-token-supplier-java.jwt")
@MicronautTest
class GcpVaultTokenSupplierTest {

    @Inject
    VaultTokenSupplier tokenSupplier;

    @Value("${gcp.vault.jwt-file}")
    String jwtFile;

    @Test
    void theCustomSupplierLogsInWithTheGcpAuthMethod() throws IOException {
        Files.writeString(Path.of(jwtFile), "signed-jwt\n");

        assertInstanceOf(GcpVaultTokenSupplier.class, tokenSupplier);
        assertEquals("my-app:signed-jwt", Mono.from(tokenSupplier.getToken()).block());
    }
}
