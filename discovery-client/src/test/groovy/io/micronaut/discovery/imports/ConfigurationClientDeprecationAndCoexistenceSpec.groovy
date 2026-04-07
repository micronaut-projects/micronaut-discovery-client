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

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.discovery.consul.config.ConsulConfigurationClient
import io.micronaut.discovery.consul.MockConsulServer
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class ConfigurationClientDeprecationAndCoexistenceSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [(MockConsulServer.ENABLED): true])

    @AutoCleanup
    @Shared
    ApplicationContext writerContext = ApplicationContext.run([
        'consul.client.host': 'localhost',
        'consul.client.port': consulServer.port
    ])

    @Shared
    ConsulClient consulClient = writerContext.getBean(ConsulClient)

    void 'legacy distributed configuration warns once with config import alternatives'() {
        given:
        def warnedKeysField = LegacyConfigurationClientDeprecationLogger.class.getDeclaredField('WARNED_KEYS')
        warnedKeysField.accessible = true
        ((Set<String>) warnedKeysField.get(null)).clear()
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, 'true')
        Flux.from(consulClient.putValue('/config/application/message', 'hello')).blockFirst()

        Logger logger = (Logger) LoggerFactory.getLogger(ConsulConfigurationClient)
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)

        when:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.application.name': 'myapp',
            'micronaut.config-client.enabled': true,
            'consul.client.config.enabled': true,
            'consul.client.host': 'localhost',
            'consul.client.port': consulServer.port
        ])

        then:
        context.getRequiredProperty('message', String) == 'hello'
        appender.list.count { it.formattedMessage.contains('deprecated') && it.formattedMessage.contains('micronaut.config.import') } == 1

        cleanup:
        logger.detachAppender(appender)
        context.close()
    }
}
