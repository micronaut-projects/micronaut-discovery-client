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
package io.micronaut.discovery.consul.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceImporter
import io.micronaut.core.util.ConnectionString
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.consul.ConsulConfiguration
import io.micronaut.discovery.consul.MockConsulServer
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.KeyValue
import io.micronaut.discovery.consul.config.ConsulConfigurationClient
import io.micronaut.discovery.consul.imports.ConsulPropertySourceImporter
import io.micronaut.http.ssl.AbstractClientSslConfiguration
import io.micronaut.http.ssl.SslConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that {@code consul.client.ssl.*} is applied to the Consul HTTP client.
 */
class ConsulClientSslConfigurationSpec extends Specification {

    static final String PASSWORD = 'changeit'

    @Shared
    Path certDir

    @Shared
    Path keyStore

    @Shared
    Path trustStore

    @Shared
    @AutoCleanup
    EmbeddedServer consulServer

    def setupSpec() {
        certDir = Files.createTempDirectory('consul-ssl')
        keyStore = certDir.resolve('consul-server.p12')
        trustStore = certDir.resolve('consul-trust.p12')
        Path cert = certDir.resolve('consul.crt')
        keytool('-genkeypair', '-alias', 'consul', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
            '-dname', 'CN=localhost', '-ext', 'SAN=dns:localhost,ip:127.0.0.1',
            '-keystore', keyStore.toString(), '-storetype', 'PKCS12', '-storepass', PASSWORD, '-keypass', PASSWORD)
        keytool('-exportcert', '-alias', 'consul', '-keystore', keyStore.toString(), '-storetype', 'PKCS12',
            '-storepass', PASSWORD, '-file', cert.toString())
        keytool('-importcert', '-noprompt', '-alias', 'consul', '-file', cert.toString(),
            '-keystore', trustStore.toString(), '-storetype', 'PKCS12', '-storepass', PASSWORD)

        consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED)               : true,
            'micronaut.server.ssl.enabled'           : true,
            'micronaut.server.ssl.port'              : -1,
            'micronaut.server.ssl.key-store.path'    : "file:${keyStore}".toString(),
            'micronaut.server.ssl.key-store.password': PASSWORD,
            'micronaut.server.ssl.key-store.type'    : 'PKCS12',
            'micronaut.server.ssl.key.password'      : PASSWORD
        ])
    }

    def cleanupSpec() {
        certDir?.toFile()?.deleteDir()
    }

    void 'consul.client.ssl settings are bound to the Consul HTTP client configuration'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'consul.client.host'                               : 'localhost',
            'consul.client.secure'                             : true,
            'consul.client.ssl.protocol'                       : 'TLSv1.3',
            'consul.client.ssl.insecure-trust-all-certificates': true,
            'consul.client.ssl.handshake-timeout'              : '5s',
            'consul.client.ssl.key-store.path'                 : 'classpath:consul-client.p12',
            'consul.client.ssl.key-store.password'             : 'key-store-secret',
            'consul.client.ssl.key-store.type'                 : 'PKCS12',
            'consul.client.ssl.key.alias'                      : 'client',
            'consul.client.ssl.key.password'                   : 'key-secret',
            'consul.client.ssl.trust-store.path'               : 'file:/tmp/consul-trust.p12',
            'consul.client.ssl.trust-store.password'           : 'trust-secret',
            'consul.client.ssl.trust-store.type'               : 'JKS'
        ])

        when:
        SslConfiguration ssl = context.getBean(ConsulConfiguration).sslConfiguration

        then:
        ssl instanceof ConsulConfiguration.ConsulSslConfiguration
        ssl.enabled
        ssl.protocol.get() == 'TLSv1.3'
        ((AbstractClientSslConfiguration) ssl).insecureTrustAllCertificates
        ssl.handshakeTimeout.seconds == 5
        ssl.keyStore.path.get() == 'classpath:consul-client.p12'
        ssl.keyStore.password.get() == 'key-store-secret'
        ssl.keyStore.type.get() == 'PKCS12'
        ssl.key.alias.get() == 'client'
        ssl.key.password.get() == 'key-secret'
        ssl.trustStore.path.get() == 'file:/tmp/consul-trust.p12'
        ssl.trustStore.password.get() == 'trust-secret'
        ssl.trustStore.type.get() == 'JKS'

        cleanup:
        context.close()
    }

    void 'consul.client.ssl.enabled=false is honoured'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'consul.client.host'       : 'localhost',
            'consul.client.ssl.enabled': false
        ])

        expect:
        !context.getBean(ConsulConfiguration).sslConfiguration.enabled

        cleanup:
        context.close()
    }

    void 'without consul.client.ssl the default client SSL configuration is unchanged'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'consul.client.host': 'localhost'
        ])

        when:
        SslConfiguration ssl = context.getBean(ConsulConfiguration).sslConfiguration

        then:
        !(ssl instanceof ConsulConfiguration.ConsulSslConfiguration)
        ssl.enabled
        !ssl.trustStore.path.isPresent()
        !((AbstractClientSslConfiguration) ssl).insecureTrustAllCertificates

        cleanup:
        context.close()
    }

    void 'the Consul and config clients trust an HTTPS Consul agent using consul.client.ssl.trust-store'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            (ConfigurationClient.ENABLED)           : true,
            'consul.client.host'                    : 'localhost',
            'consul.client.port'                    : consulServer.port,
            'consul.client.secure'                  : true,
            'consul.client.ssl.trust-store.path'    : "file:${trustStore}".toString(),
            'consul.client.ssl.trust-store.password': PASSWORD,
            'consul.client.ssl.trust-store.type'    : 'PKCS12'
        ], Environment.TEST)
        ConsulClient client = context.getBean(ConsulClient)

        when:
        Boolean written = Flux.from(client.putValue('config/application/ssl.message', 'secured')).blockFirst()
        List<KeyValue> values = Flux.from(client.readValues('config/application')).blockFirst()

        then:
        written
        values*.key.contains('config/application/ssl.message')

        when: 'the config client reads through the same HTTPS client'
        List<PropertySource> sources = Flux.from(context.getBean(ConsulConfigurationClient).getPropertySources(context.environment)).collectList().block()

        then:
        sources.any { it.get('ssl.message') == 'secured' }

        cleanup:
        context.close()
    }

    void 'the Consul client accepts a self-signed HTTPS Consul agent with insecure-trust-all-certificates'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'consul.client.host'                               : 'localhost',
            'consul.client.port'                               : consulServer.port,
            'consul.client.secure'                             : true,
            'consul.client.ssl.insecure-trust-all-certificates': true
        ])

        when:
        Boolean written = Flux.from(context.getBean(ConsulClient).putValue('config/application/insecure', 'ok')).blockFirst()

        then:
        written

        cleanup:
        context.close()
    }

    void 'the Consul client rejects the self-signed HTTPS Consul agent without consul.client.ssl'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'consul.client.host'  : 'localhost',
            'consul.client.port'  : consulServer.port,
            'consul.client.secure': true
        ])

        when:
        Flux.from(context.getBean(ConsulClient).putValue('config/application/untrusted', 'nope')).blockFirst()

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void 'the consul:// import reuses consul.client.ssl settings from the importing configuration'() {
        given:
        EmbeddedServer plainConsul = ApplicationContext.run(EmbeddedServer, [(MockConsulServer.ENABLED): true])
        ApplicationContext importing = ApplicationContext.run([
            'consul.client.ssl.insecure-trust-all-certificates': true,
            'consul.client.ssl.protocol'                       : 'TLSv1.3',
            'consul.client.ssl.trust-store.path'               : "file:${trustStore}".toString(),
            'consul.client.ssl.trust-store.password'           : PASSWORD
        ])
        ConsulPropertySourceImporter importer = new ConsulPropertySourceImporter()
        ConnectionString connectionString = ConnectionString.parse("consul://localhost:${plainConsul.port}/config/application")
        def declaration = importer.newImportDeclaration(connectionString)
        PropertySourceImporter.ImportContext importContext = Stub() {
            environment() >> importing.environment
            connectionString() >> connectionString
            importDeclaration() >> declaration
        }

        when:
        importer.importPropertySource(importContext)
        SslConfiguration ssl = ((ApplicationContext) importer.@applicationContext).getBean(ConsulConfiguration).sslConfiguration

        then:
        ssl instanceof ConsulConfiguration.ConsulSslConfiguration
        ((AbstractClientSslConfiguration) ssl).insecureTrustAllCertificates
        ssl.protocol.get() == 'TLSv1.3'
        ssl.trustStore.path.get() == "file:${trustStore}".toString()
        ssl.trustStore.password.get() == PASSWORD

        cleanup:
        importer.close()
        importing.close()
        plainConsul.close()
    }

    static void keytool(String... args) {
        String keytool = Path.of(System.getProperty('java.home'), 'bin', 'keytool').toString()
        Process process = new ProcessBuilder([keytool, *args]).redirectErrorStream(true).start()
        String output = process.inputStream.text
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool ${args.toList()} failed: ${output}")
        }
    }
}
