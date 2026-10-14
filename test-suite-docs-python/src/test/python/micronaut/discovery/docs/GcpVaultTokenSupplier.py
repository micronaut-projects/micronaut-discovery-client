from micronaut.context.annotation import Requires
# tag::imports[]
from pathlib import Path
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import BootstrapContextCompatible, Value
from micronaut.discovery.vault.config.auth import VaultAuthHttpClient, VaultTokenSupplier
from org.reactivestreams import Publisher
from reactor.core.publisher import Mono
# end::imports[]


@Requires(property="spec.name", value="GcpVaultTokenSupplierTest")
# tag::class[]
@Singleton
@BootstrapContextCompatible  # <1>
class GcpVaultTokenSupplier(VaultTokenSupplier):

    def __init__(self, auth_client: VaultAuthHttpClient,
                 jwt_file: Annotated[str, Value("${gcp.vault.jwt-file}")]) -> None:
        self.auth_client = auth_client
        self.jwt_file = Path(jwt_file)

    def getToken(self) -> Publisher[str]:
        return (Mono.fromCallable(lambda: self.jwt_file.read_text().strip())  # <2>
                .flatMap(lambda jwt: Mono.from_(self.auth_client.login("gcp", {"role": "my-app", "jwt": jwt})))  # <3>
                .map(lambda response: response.auth().clientToken()))
# end::class[]
