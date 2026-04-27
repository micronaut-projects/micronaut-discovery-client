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
import org.testcontainers.DockerClientFactory
import org.testcontainers.vault.VaultContainer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class VaultPropertySourceImporterIntegrationSpec extends Specification {

    @Shared
    @AutoCleanup
    VaultContainer vaultContainer = new VaultContainer("vault:1.2.3")
        .withSecretInVault("secret/application",
            "my.secret=hello-from-vault"
        )
        .withVaultToken("testtoken")

    void 'vault importer resolves explicit secret path against real container'() {
        given:
        vaultContainer.start()
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.config.import': "vault://testtoken@${vaultContainer.host}:${vaultContainer.firstMappedPort}/application?kv-version=V2&secret-engine-name=secret"
        ], Environment.TEST)

        then:
        context.getRequiredProperty('my.secret', String) == 'hello-from-vault'

        cleanup:
        context.close()
    }
}
