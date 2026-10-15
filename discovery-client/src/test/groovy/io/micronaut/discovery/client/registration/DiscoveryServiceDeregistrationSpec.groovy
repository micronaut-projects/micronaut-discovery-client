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
package io.micronaut.discovery.client.registration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.registration.AutoRegistration
import io.micronaut.discovery.registration.RegistrationConfiguration
import io.micronaut.health.HealthStatus
import io.micronaut.http.HttpStatus
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verifies the blocking and the non-blocking de-registration of {@link DiscoveryServiceAutoRegistration}.
 */
class DiscoveryServiceDeregistrationSpec extends Specification {

    RegistrationConfiguration registrationConfiguration = new RegistrationConfiguration() {}

    TestAutoRegistration autoRegistration = new TestAutoRegistration(registrationConfiguration)

    List<ILoggingEvent> events = new CopyOnWriteArrayList<>()

    AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
        @Override
        protected void append(ILoggingEvent event) {
            events << event
        }
    }

    Logger logger = (Logger) LoggerFactory.getLogger(AutoRegistration)

    void setup() {
        appender.start()
        logger.addAppender(appender)
        autoRegistration.@registered.set(true)
    }

    void cleanup() {
        logger.detachAppender(appender)
    }

    @Unroll
    void 'a successful de-registration is logged and resets the registration (block: #block)'() {
        when:
        autoRegistration.performDeregistration('Test', registrationConfiguration, Mono.just(HttpStatus.OK), 'test-app', block)

        then:
        !autoRegistration.@registered.get()
        events.any { it.level == Level.INFO && it.formattedMessage == 'De-registered service [test-app] with Test' }
        !events.any { it.level == Level.ERROR }

        where:
        block << [true, false]
    }

    @Unroll
    void 'a failed de-registration is logged and resets the registration (block: #block)'() {
        when:
        autoRegistration.performDeregistration('Test', registrationConfiguration, Mono.error(new IllegalStateException('boom')), 'test-app', block)

        then:
        !autoRegistration.@registered.get()
        events.any { it.level == Level.ERROR && it.formattedMessage == 'Error occurred de-registering service [test-app] with Test: boom' }
        !events.any { it.formattedMessage.startsWith('De-registered service') }

        where:
        block << [true, false]
    }

    void 'the de-registration blocks according to the fail-fast setting by default'() {
        given:
        registrationConfiguration.failFast = failFast

        when:
        autoRegistration.performDeregistration('Test', registrationConfiguration, Mono.just(HttpStatus.OK), 'test-app')

        then:
        !autoRegistration.@registered.get()
        events.any { it.formattedMessage == 'De-registered service [test-app] with Test' }

        where:
        failFast << [true, false]
    }

    static class TestAutoRegistration extends DiscoveryServiceAutoRegistration {

        TestAutoRegistration(RegistrationConfiguration registrationConfiguration) {
            super(registrationConfiguration)
        }

        @Override
        protected void pulsate(ServiceInstance instance, HealthStatus status) {
        }

        @Override
        protected void deregister(ServiceInstance instance) {
        }

        @Override
        protected void register(ServiceInstance instance) {
        }
    }
}
