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
package io.micronaut.discovery.vault.imports

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.discovery.vault.config.AbstractVaultResponse
import io.micronaut.discovery.vault.config.VaultClientConfiguration
import io.micronaut.discovery.vault.config.VaultConfigHttpClient
import io.micronaut.http.HttpStatus
import io.micronaut.http.simple.SimpleHttpResponseFactory
import io.micronaut.http.client.exceptions.HttpClientResponseException
import reactor.core.publisher.Flux
import spock.lang.Specification

class VaultImportSupportSpec extends Specification {

    private final VaultImportSupport support = new VaultImportSupport()
    private final ApplicationContext context = Mock()
    private final VaultClientConfiguration configuration = Mock()
    private final VaultConfigHttpClient<AbstractVaultResponse<?>> client = Mock()

    void 'load returns empty map for optional deserialization error'() {
        given:
        context.getBean(VaultClientConfiguration) >> configuration
        context.getBean(VaultConfigHttpClient) >> client
        configuration.getToken() >> 'token'
        configuration.getSecretEngineName() >> 'secret'
        configuration.getPathPrefix() >> null
        client.readConfigurationValues('token', 'secret', 'application') >> Flux.error(new HttpClientResponseException(
            'Cannot deserialize value of type `java.util.LinkedHashMap` from Array value',
            new SimpleHttpResponseFactory().status(HttpStatus.BAD_REQUEST)
        ))

        expect:
        support.load(context, 'application', true).isEmpty()
    }

    void 'load prefixes secret path when path prefix configured'() {
        given:
        context.getBean(VaultClientConfiguration) >> configuration
        context.getBean(VaultConfigHttpClient) >> client
        configuration.getToken() >> 'token'
        configuration.getSecretEngineName() >> 'secret'
        configuration.getPathPrefix() >> 'config/base'
        client.readConfigurationValues('token', 'secret', 'config/base/application') >> Flux.just(response([message: 'hello']))

        when:
        Map<String, Object> result = support.load(context, '/application', false)

        then:
        result == [message: 'hello']
    }

    void 'load throws configuration exception for non optional not found'() {
        given:
        context.getBean(VaultClientConfiguration) >> configuration
        context.getBean(VaultConfigHttpClient) >> client
        configuration.getToken() >> 'token'
        configuration.getSecretEngineName() >> 'secret'
        configuration.getPathPrefix() >> null
        client.readConfigurationValues('token', 'secret', 'application') >> Flux.error(new HttpClientResponseException('missing', new SimpleHttpResponseFactory().status(HttpStatus.NOT_FOUND)))

        when:
        support.load(context, 'application', false)

        then:
        ConfigurationException e = thrown()
        e.message.contains('Error reading distributed configuration from Vault: missing')
    }

    private AbstractVaultResponse<?> response(Map<String, Object> secrets) {
        Stub(AbstractVaultResponse) {
            getSecrets() >> secrets
        }
    }
}
