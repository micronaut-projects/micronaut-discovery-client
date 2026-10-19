package io.micronaut.discovery.vault.health

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

/**
 * Stubs Vault's {@code GET /v1/sys/health} endpoint, answering with the status code configured
 * through {@link #CODE} and a body matching the documented semantics of that code.
 */
@Controller("/v1/sys")
@Requires(property = MockVaultHealthServer.ENABLED)
class MockVaultHealthServer {

    static final String ENABLED = 'enable.mock.vault-health'
    static final String CODE = 'mock.vault-health.code'

    private final int code

    MockVaultHealthServer(@Value('${mock.vault-health.code:200}') int code) {
        this.code = code
    }

    @Get(uri = "/health", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> health() {
        Map<String, Object> body = [
                initialized                 : code != 501,
                sealed                      : code == 503 || code == 501,
                standby                     : code == 429 || code == 473,
                performance_standby         : code == 473,
                replication_performance_mode: 'disabled',
                replication_dr_mode         : code == 472 ? 'secondary' : 'disabled',
                server_time_utc             : 1700000000,
                version                     : '1.17.2',
                cluster_name                : 'vault-cluster-test',
                cluster_id                  : 'b3e2a6c4-0000-0000-0000-000000000000'
        ]
        return HttpResponse.<Map<String, Object>>status(code, 'Vault ' + code).body(body)
    }
}
