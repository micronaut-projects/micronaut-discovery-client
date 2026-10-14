package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

/**
 * Stands in for the Vault GCP auth method: the client token echoes the role and the JWT of the login request.
 */
@Requires(property = "spec.name", value = "GcpVaultTokenSupplierSpec")
@Controller("/v1/auth/gcp")
class GcpVaultAuthController {

    @Post("/login")
    String login(String role, String jwt) {
        """{"auth":{"client_token":"$role:$jwt"}}"""
    }
}
