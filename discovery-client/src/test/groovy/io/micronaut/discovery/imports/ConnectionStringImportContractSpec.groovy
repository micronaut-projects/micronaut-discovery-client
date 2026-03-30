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
import io.micronaut.core.util.ConnectionString.HostPort
import spock.lang.Specification

class ConnectionStringImportContractSpec extends Specification {

    void 'parses explicit Consul import URIs'() {
        when:
        ConnectionString connectionString = ConnectionString.parse('consul://localhost:8500/config/application?format=json')

        then:
        connectionString.getProtocol() == 'consul'
        connectionString.getHosts() == [new HostPort('localhost', 8500)]
        connectionString.getPath() == 'config/application'
        connectionString.getOptions().get('format') == 'json'
    }

    void 'parses explicit Vault import URIs'() {
        when:
        ConnectionString connectionString = ConnectionString.parse('vault://localhost:8200/application/prod?retry-attempts=3&retry-delay=1s')

        then:
        connectionString.getProtocol() == 'vault'
        connectionString.getHosts() == [new HostPort('localhost', 8200)]
        connectionString.getPath() == 'application/prod'
        connectionString.getOptions().get('retry-attempts') == '3'
        connectionString.getOptions().get('retry-delay') == '1s'
    }

    void 'parses explicit Spring Cloud import URIs'() {
        when:
        ConnectionString connectionString = ConnectionString.parse('springcloud://localhost:8888/myapp/default?label=main')

        then:
        connectionString.getProtocol() == 'springcloud'
        connectionString.getHosts() == [new HostPort('localhost', 8888)]
        connectionString.getPath() == 'myapp/default'
        connectionString.getOptions().get('label') == 'main'
    }

    void 'rejects missing path imports'() {
        when:
        ConnectionString connectionString = ConnectionString.parse(value)
        validatePath(connectionString)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Connection string path is required for parse mode PATH: ${value}"

        where:
        value << [
                'consul://localhost:8500',
                'vault://localhost:8200',
                'springcloud://localhost:8888'
        ]
    }

    private static void validatePath(ConnectionString connectionString) {
        String path = connectionString.getPath()
        if (path == null || path == '/' || path.isBlank()) {
            throw new ConfigurationException("Import URI must include an explicit path: ${connectionString}")
        }
    }
}
