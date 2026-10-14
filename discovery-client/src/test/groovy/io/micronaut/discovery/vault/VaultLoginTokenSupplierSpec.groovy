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
package io.micronaut.discovery.vault

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.discovery.vault.config.auth.AbstractVaultLoginTokenSupplier
import io.micronaut.discovery.vault.config.auth.VaultAuthHttpClient
import io.micronaut.discovery.vault.config.auth.VaultLoginResponse
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class VaultLoginTokenSupplierSpec extends Specification {

    void 'overlapping token requests share one login'() {
        given: 'an auth method whose credentials can be used once'
        SingleUseAuthClient client = new SingleUseAuthClient()
        TestTokenSupplier supplier = new TestTokenSupplier(client)

        when: 'two callers request a token while the login is in progress'
        def tokens = Mono.zip(Mono.from(supplier.getToken()), Mono.from(supplier.getToken())).block(Duration.ofSeconds(10))

        then:
        tokens.t1 == 'token-1'
        tokens.t2 == 'token-1'
        client.logins.get() == 1

        and: 'later callers reuse the token'
        Mono.from(supplier.getToken()).block() == 'token-1'
        client.logins.get() == 1
    }

    void 'a failed login is retried by the next caller'() {
        given:
        FailingOnceAuthClient client = new FailingOnceAuthClient()
        TestTokenSupplier supplier = new TestTokenSupplier(client)

        when:
        Mono.from(supplier.getToken()).block()

        then:
        thrown(ConfigurationException)

        when:
        String token = Mono.from(supplier.getToken()).block()

        then:
        token == 'token-2'
        client.logins.get() == 2
    }

    static class TestTokenSupplier extends AbstractVaultLoginTokenSupplier {
        TestTokenSupplier(VaultAuthHttpClient client) {
            super(client)
        }

        @Override
        protected String getMethodName() {
            'test'
        }

        @Override
        protected String getMountPath() {
            'test'
        }

        @Override
        protected Map<String, Object> getLoginBody() {
            [secret_id: 'single-use']
        }
    }

    static class SingleUseAuthClient implements VaultAuthHttpClient {
        final AtomicInteger logins = new AtomicInteger()

        @Override
        Publisher<VaultLoginResponse> login(String mountPath, Map<String, Object> body) {
            Mono.defer {
                int login = logins.incrementAndGet()
                login == 1
                        ? Mono.delay(Duration.ofMillis(300)).map { response("token-$login") }
                        : Mono.error(new IllegalStateException('invalid secret id'))
            }
        }
    }

    static class FailingOnceAuthClient implements VaultAuthHttpClient {
        final AtomicInteger logins = new AtomicInteger()

        @Override
        Publisher<VaultLoginResponse> login(String mountPath, Map<String, Object> body) {
            Mono.defer {
                int login = logins.incrementAndGet()
                login == 1 ? Mono.error(new IllegalStateException('Vault unavailable')) : Mono.just(response("token-$login"))
            }
        }
    }

    private static VaultLoginResponse response(String token) {
        new VaultLoginResponse(new VaultLoginResponse.Auth(token, 3600, true, ['default']))
    }
}
