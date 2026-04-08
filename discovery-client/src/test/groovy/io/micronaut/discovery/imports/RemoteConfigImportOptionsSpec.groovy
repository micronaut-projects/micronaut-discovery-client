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
package io.micronaut.discovery.imports

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.util.ConnectionString
import spock.lang.Specification

class RemoteConfigImportOptionsSpec extends Specification {

    private final RemoteConfigImportOptionBinder binder = new RemoteConfigImportOptionBinder()

    void 'binds common resiliency options and normalizes retry-count alias'() {
        when:
        Map<String, Object> options = binder.bind(ConnectionString.parse('consul://localhost:8500/config/application?retry-count=2&retry-attempts=3&retry-delay=1s&read-timeout=5s&connect-timeout=2s'))

        then:
        options['retry-attempts'] == '3'
        options['retry-delay'] == '1s'
        options['read-timeout'] == '5s'
        options['connect-timeout'] == '2s'
    }

    void 'binds provider-specific options for each supported protocol'() {
        expect:
        binder.bind(ConnectionString.parse('consul://localhost:8500/config/application?format=json&dc=dc1&acl-token=token&fail-fast=true')) == [
            'consul.client.config.format': 'json',
            'consul.client.config.datacenter': 'dc1',
            'consul.client.acl-token': 'token',
            'consul.client.config.fail-fast': 'true'
        ]

        binder.bind(ConnectionString.parse('vault://localhost:8200/application?token=abc&kv-version=V1&secret-engine-name=secret&path-prefix=team&fail-fast=true')) == [
            'vault.client.token': 'abc',
            'vault.client.kv-version': 'V1',
            'vault.client.secret-engine-name': 'secret',
            'vault.client.path-prefix': 'team',
            'vault.client.fail-fast': 'true'
        ]

        binder.bind(ConnectionString.parse('springcloud://localhost:8888/myapp/default?label=main&username=user&password=secret&fail-fast=true')) == [
            'spring.cloud.config.label': 'main',
            'spring.cloud.config.username': 'user',
            'spring.cloud.config.password': 'secret',
            'spring.cloud.config.fail-fast': 'true'
        ]
    }

    void 'binds provider credentials from connection string user info'() {
        expect:
        binder.bind(ConnectionString.parse('vault://testtoken@localhost:8200/application')) == [
            'vault.client.token': 'testtoken'
        ]

        binder.bind(ConnectionString.parse('springcloud://user:secret@localhost:8888/myapp/default')) == [
            'spring.cloud.config.username': 'user',
            'spring.cloud.config.password': 'secret'
        ]
    }

    void 'rejects unsupported query parameter for protocol'() {
        when:
        binder.bind(ConnectionString.parse('springcloud://localhost:8888/myapp/default?name=forbidden'))

        then:
        ConfigurationException e = thrown()
        e.message.contains("Unsupported query parameter 'name'")
        e.message.contains("protocol 'springcloud'")
    }
}
