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

import io.micronaut.core.util.ConnectionString
import spock.lang.Specification

class RemoteConfigImporterCommonSettingsSpec extends Specification {

    private final RemoteConfigImportOptionBinder binder = new RemoteConfigImportOptionBinder()

    void 'common retry and timeout settings are bound for all supported protocols'() {
        expect:
        binder.bind(ConnectionString.parse(uri))['retry-attempts'] == '3'
        binder.bind(ConnectionString.parse(uri))['retry-delay'] == '1s'
        binder.bind(ConnectionString.parse(uri))['read-timeout'] == '5s'
        binder.bind(ConnectionString.parse(uri))['connect-timeout'] == '2s'

        where:
        uri << [
            'consul://localhost:8500/config/application?retry-attempts=3&retry-delay=1s&read-timeout=5s&connect-timeout=2s',
            'vault://token@localhost:8200/application?retry-attempts=3&retry-delay=1s&read-timeout=5s&connect-timeout=2s',
            'springcloud://user:secret@localhost:8888/myapp/default?retry-attempts=3&retry-delay=1s&read-timeout=5s&connect-timeout=2s'
        ]
    }
}
