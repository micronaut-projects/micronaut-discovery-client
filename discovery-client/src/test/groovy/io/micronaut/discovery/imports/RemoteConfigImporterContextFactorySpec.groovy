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

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.client.config.DistributedPropertySourceLocator
import io.micronaut.discovery.config.ConfigurationClient
import spock.lang.AutoCleanup
import spock.lang.Specification

class RemoteConfigImporterContextFactorySpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    void 'builds minimal context with legacy distributed configuration disabled'() {
        given:
        RemoteConfigImporterContextFactory factory = new RemoteConfigImporterContextFactory()

        when:
        context = factory.build([
            'vault.client.uri': 'http://localhost:8200',
            'vault.client.token': 'testtoken'
        ])

        then:
        !context.containsBean(DistributedPropertySourceLocator)
        !context.environment.getRequiredProperty(ConfigurationClient.ENABLED, Boolean)
        !context.environment.getRequiredProperty('spring.cloud.config.enabled', Boolean)
        context.environment.getRequiredProperty('vault.client.config.enabled', Boolean)
    }
}
