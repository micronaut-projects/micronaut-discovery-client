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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class ConsulImportRetrySpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ConsulImportRetrySpec'])

    @Shared
    FlakyConsulKvController controller = consulServer.applicationContext.getBean(FlakyConsulKvController)

    void 'consul import retries using the retry options of the import URI'() {
        given: 'Consul fails more often than the Consul client retries on its own'
        controller.reset(5)

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "consul://localhost:${consulServer.port}/config/application?retry-attempts=2&retry-delay=10ms&retry-multiplier=2.0&retry-max-delay=5s&retry-jitter=0.1"
        ])

        then:
        context.getRequiredProperty('message', String) == 'hello'
        controller.calls.get() > 5

        cleanup:
        context?.close()
    }

    void 'consul import fails startup once the retry attempts are exhausted'() {
        given:
        controller.reset(Integer.MAX_VALUE)

        when:
        ApplicationContext.run([
            'micronaut.config.import': "consul://localhost:${consulServer.port}/config/application?retry-attempts=1"
        ])

        then:
        thrown(Exception)
    }

    @Controller('/v1')
    @Requires(property = 'spec.name', value = 'ConsulImportRetrySpec')
    static class FlakyConsulKvController {

        final AtomicInteger calls = new AtomicInteger()
        volatile int failures

        void reset(int failures) {
            this.failures = failures
            calls.set(0)
        }

        @Get(uri = '/kv/{+key}', produces = MediaType.APPLICATION_JSON)
        HttpResponse<String> readValues(String key) {
            if (calls.incrementAndGet() <= failures) {
                return HttpResponse.serverError()
            }
            String value = Base64.encoder.encodeToString('hello'.bytes)
            HttpResponse.ok('[{"Key":"config/application/message","Value":"' + value + '"}]'.toString())
        }
    }
}
