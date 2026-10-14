/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.vault.health

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.health.HealthStatus
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * Verifies that a Vault health endpoint answering 404 is reported as DOWN. The Micronaut Reactor HTTP
 * client turns a 404 response into an empty result, so subclasses run with and without it.
 */
abstract class AbstractVaultHealthNotFoundSpec extends Specification {

    static final String SPEC_NAME = 'VaultHealthNotFoundSpec'

    void 'a Vault health endpoint answering 404 is reported as DOWN'() {
        given:
        EmbeddedServer vault = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])
        ApplicationContext context = ApplicationContext.run([
                'vault.client.config.enabled': true,
                'vault.client.uri'           : vault.URL.toString()
        ])

        when:
        HealthResult result = Mono.from(context.getBean(VaultHealthIndicator).result).block()

        then:
        result != null
        result.name == VaultHealthIndicator.NAME
        result.status == HealthStatus.DOWN
        (result.details as Map).state == 'unknown'

        cleanup:
        context?.close()
        vault?.close()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/v1/sys')
    static class NotFoundHealthController {

        @Get('/health')
        HttpResponse<?> health() {
            HttpResponse.notFound()
        }
    }
}
