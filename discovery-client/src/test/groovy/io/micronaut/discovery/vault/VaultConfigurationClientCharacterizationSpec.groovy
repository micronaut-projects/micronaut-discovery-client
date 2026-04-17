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
import io.micronaut.discovery.client.config.DistributedPropertySourceLocator
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.vault.config.VaultConfigurationClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class VaultConfigurationClientCharacterizationSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer v1Server = ApplicationContext.run(EmbeddedServer, [(MockingVaultServerV1Controller.ENABLED): true])

    @Shared
    @AutoCleanup
    EmbeddedServer v2Server = ApplicationContext.run(EmbeddedServer, [(MockingVaultServerV2Controller.ENABLED): true])

    void 'legacy Vault exposes only the composite ConfigurationClient without the bootstrap locator until ConfigurationClient.ENABLED is set'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
                'vault.client.uri': v2Server.URL.toString(),
                'vault.client.config.enabled': true,
                'vault.client.token': 'testtoken'
        ])

        then:
        context.containsBean(ConfigurationClient)
        !context.containsBean(VaultConfigurationClient)
        !context.containsBean(DistributedPropertySourceLocator)

        cleanup:
        context.close()
    }

    void 'legacy Vault V1 configuration preserves explicit bootstrap ordering'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
                (MockingVaultServerV1Controller.ENABLED): true,
                'micronaut.application.name': 'myapp',
                'micronaut.config-client.enabled': true,
                'vault.client.config.enabled': true,
                'vault.client.kv-version': 'V1',
                'vault.client.token': 'testtoken',
                'vault.client.secret-engine-name': 'backendv1',
                'vault.client.uri': v1Server.URL.toString()
        ], 'first', 'second')

        then:
        context.getRequiredProperty('v1-secret-1', Integer) == 1
        context.getRequiredProperty('v1-secret-2', Integer) == 1
        context.getRequiredProperty('v1-secret-3', Integer) == 1
        context.getRequiredProperty('v1-secret-4', Integer) == 1
        context.getRequiredProperty('v1-secret-5', Integer) == 1
        context.getRequiredProperty('v1-secret-6', Integer) == 1

        cleanup:
        context.close()
    }

    void 'legacy Vault V2 configuration preserves path-prefix handling and client bean creation'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
                (MockingVaultServerV2Controller.ENABLED): true,
                'micronaut.application.name': 'myapp',
                'micronaut.config-client.enabled': true,
                'vault.client.config.enabled': true,
                'vault.client.kv-version': 'V2',
                'vault.client.token': 'testtoken',
                'vault.client.path-prefix': MockingVaultServerV2Controller.PATH_PREFIX,
                'vault.client.secret-engine-name': 'backendv2-prefixed',
                'vault.client.uri': v2Server.URL.toString()
        ], 'first', 'second')

        then:
        context.containsBean(VaultConfigurationClient)
        context.containsBean(DistributedPropertySourceLocator)
        context.getRequiredProperty('v2-prefixed-secret-1', Integer) == 1
        context.getRequiredProperty('v2-prefixed-secret-2', Integer) == 1
        context.getRequiredProperty('v2-prefixed-secret-3', Integer) == 1
        context.getRequiredProperty('v2-prefixed-secret-4', Integer) == 1
        context.getRequiredProperty('v2-prefixed-secret-5', Integer) == 1
        context.getRequiredProperty('v2-prefixed-secret-6', Integer) == 1

        cleanup:
        context.close()
    }
}
