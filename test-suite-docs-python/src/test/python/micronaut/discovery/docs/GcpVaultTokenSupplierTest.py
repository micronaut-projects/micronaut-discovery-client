from pathlib import Path
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property, Value
from micronaut.discovery.vault.config.auth import VaultTokenSupplier
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from reactor.core.publisher import Mono

from .GcpVaultTokenSupplier import GcpVaultTokenSupplier


@Property(name="spec.name", value="GcpVaultTokenSupplierTest")
@Property(name="vault.client.uri", value="http://localhost:${micronaut.server.port}")
@Property(name="vault.client.config.enabled", value="true")
@Property(name="gcp.vault.jwt-file", value="${java.io.tmpdir}/gcp-vault-token-supplier-python.jwt")
@MicronautTest
class GcpVaultTokenSupplierTest:

    token_supplier: Annotated[VaultTokenSupplier, Inject]
    jwt_file: Annotated[str, Value("${gcp.vault.jwt-file}")]

    @Test
    def the_custom_supplier_logs_in_with_the_gcp_auth_method(self) -> None:
        Path(self.jwt_file).write_text("signed-jwt\n")

        assert isinstance(self.token_supplier, GcpVaultTokenSupplier)
        assert Mono.from_(self.token_supplier.getToken()).block() == "my-app:signed-jwt"
