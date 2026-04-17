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
package io.micronaut.discovery.consul.imports

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.KeyValue
import io.micronaut.discovery.imports.RemoteConfigImportOptionBinder
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.Base64

class ConsulImportSupportSpec extends Specification {

    private final ConsulImportSupport support = new ConsulImportSupport()
    private final ApplicationContext context = Mock()
    private final ConsulClient consulClient = Mock()

    void 'load throws for unsupported format'() {
        given:
        context.getBean(ConsulClient) >> consulClient
        consulClient.readValues('/config/application', null, null, null) >> Flux.just([keyValue('/config/application', '{"message":"hello"}')])

        when:
        support.load(context, '/config/application', 'toml', null)

        then:
        ConfigurationException e = thrown()
        e.message == 'Unsupported consul import format: toml'
    }

    void 'load file format skips entries without supported extension'() {
        given:
        context.getBean(ConsulClient) >> consulClient
        consulClient.readValues('/config/application', null, null, null) >> Flux.just([
            keyValue('config/noextension', 'ignored'),
            keyValue('config/notes.txt', 'ignored')
        ])

        when:
        Map<String, Object> result = support.load(context, '/config/application', RemoteConfigImportOptionBinder.FORMAT_FILE, null)

        then:
        result.isEmpty()
    }

    void 'resolveWatchPath only returns path for native non-empty imports'() {
        expect:
        support.resolveWatchPath('/config/application', format, importedValues) == expected

        where:
        format                                         | importedValues     || expected
        RemoteConfigImportOptionBinder.FORMAT_NATIVE   | [message: 'hello'] || Optional.of('/config/application')
        RemoteConfigImportOptionBinder.FORMAT_JSON     | [message: 'hello'] || Optional.empty()
        RemoteConfigImportOptionBinder.FORMAT_NATIVE   | [:]                || Optional.empty()
    }

    private KeyValue keyValue(String key, String value) {
        Stub(KeyValue) {
            getKey() >> key
            getValue() >> Base64.encoder.encodeToString(value.bytes)
        }
    }
}
