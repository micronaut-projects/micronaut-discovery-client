from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Post


# Stands in for the Vault GCP auth method: the client token echoes the role and the JWT of the login request.
@Requires(property="spec.name", value="GcpVaultTokenSupplierTest")
@Controller("/v1/auth/gcp")
class GcpVaultAuthController:

    @Post("/login")
    def login(self, role: str, jwt: str) -> str:
        return '{"auth":{"client_token":"' + role + ":" + jwt + '"}}'
