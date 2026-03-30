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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class VaultPropertySourceImporterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer v1Server = ApplicationContext.run(EmbeddedServer, [(MockingVaultServerV1Controller.ENABLED): true])

    @Shared
    @AutoCleanup
    EmbeddedServer v2Server = ApplicationContext.run(EmbeddedServer, [(MockingVaultServerV2Controller.ENABLED): true])

    void 'vault importer resolves explicit V1 secret path'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "vault://localhost:${v1Server.port}/application?token=testtoken&kv-version=V1&secret-engine-name=backendv1"
        ])

        then:
        context.getRequiredProperty('v1-secret-1', Integer) == 6

        cleanup:
        context.close()
    }

    void 'vault importer resolves explicit V2 secret path with prefix'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "vault://localhost:${v2Server.port}/application?token=testtoken&kv-version=V2&secret-engine-name=backendv2-prefixed&path-prefix=mock/v2/path/prefix"
        ])

        then:
        context.getRequiredProperty('v2-prefixed-secret-1', Integer) == 6

        cleanup:
        context.close()
    }

    void 'optional vault import suppresses empty mock response'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "optional:vault://localhost:${v2Server.port}/missing?token=testtoken&kv-version=V2&secret-engine-name=backendv2"
        ])

        then:
        !context.environment.getProperty('v2-secret-1', Integer).isPresent()

        cleanup:
        context.close()
    }

    void 'non optional vault import fails on empty mock response'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext.run([
            'micronaut.config.import': "vault://localhost:${v2Server.port}/missing?token=testtoken&kv-version=V2&secret-engine-name=backendv2"
        ])

        then:
        thrown(ConfigurationException)
    }
}
