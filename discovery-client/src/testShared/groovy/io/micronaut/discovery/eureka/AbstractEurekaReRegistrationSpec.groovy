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
package io.micronaut.discovery.eureka

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.eureka.registration.EurekaAutoRegistration
import io.micronaut.discovery.registration.AutoRegistration
import io.micronaut.health.CurrentHealthStatus
import io.micronaut.health.HealthStatus
import io.micronaut.health.HeartbeatTask
import io.micronaut.http.client.ReactiveClientResultTransformer
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verifies that a service re-registers with Eureka when Eureka no longer knows the instance and
 * answers the heartbeat or a status update with a 404. The Micronaut Reactor HTTP client turns a
 * 404 response into an empty publisher, so subclasses run with and without it. Also verifies the
 * de-registration that follows a failed fail-fast registration.
 */
abstract class AbstractEurekaReRegistrationSpec extends Specification {

    @AutoCleanup
    EmbeddedServer eureka = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                  : EurekaReRegistrationStub.SPEC_NAME,
            'micronaut.heartbeat.enabled': false,
    ])

    EurekaReRegistrationStub stub = eureka.applicationContext.getBean(EurekaReRegistrationStub)

    PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.2)

    /**
     * @return The number of {@link ReactiveClientResultTransformer} beans expected on the test classpath
     */
    abstract int expectedResultTransformers()

    void 'the service re-registers when Eureka answers a heartbeat with a 404'() {
        given:
        EmbeddedServer server = startService()

        expect:
        server.applicationContext.getBeansOfType(ReactiveClientResultTransformer).size() == expectedResultTransformers()

        when: 'a heartbeat reports the status of the registered service'
        awaitRegistration(server)
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then:
        conditions.eventually {
            assert stub.heartbeats.get() == 1
            assert stub.status.get() == 'UP'
        }

        when: 'Eureka forgets the instance and a heartbeat is reported'
        stub.forget()
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then: 'the service is registered again'
        conditions.eventually {
            assert stub.heartbeats.get() == 2
            assert stub.registrations.get() == 2
        }

        cleanup:
        server?.close()
    }

    void 'the service re-registers when Eureka answers a status update with a 404'() {
        given:
        EmbeddedServer server = startService()

        expect:
        server.applicationContext.getBeansOfType(ReactiveClientResultTransformer).size() == expectedResultTransformers()

        when: 'a heartbeat reports the status of the registered service'
        awaitRegistration(server)
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then:
        conditions.eventually {
            assert stub.statusUpdates.get() == 1
            assert stub.status.get() == 'UP'
        }

        when: 'Eureka forgets the instance and a heartbeat reports a new status'
        stub.forget()
        server.applicationContext.getBean(CurrentHealthStatus).update(HealthStatus.DOWN)
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then: 'the service is registered again'
        conditions.eventually {
            assert stub.statusUpdates.get() == 2
            assert stub.registrations.get() == 2
            assert stub.registered.get()
        }
        // a DOWN heartbeat does not renew the lease
        stub.heartbeats.get() == 1

        when: 'the next heartbeat is reported'
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then: 'the status Eureka did not record is reported again'
        conditions.eventually {
            assert stub.statusUpdates.get() == 3
            assert stub.status.get() == 'DOWN'
        }
        stub.registrations.get() == 2

        cleanup:
        server?.close()
    }

    void 'the service de-registers without blocking the event loop when a fail-fast registration fails'() {
        given:
        List<ILoggingEvent> events = new CopyOnWriteArrayList<>()
        AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                events << event
            }
        }
        appender.start()
        Logger logger = (Logger) LoggerFactory.getLogger(AutoRegistration)
        logger.addAppender(appender)
        stub.failRegistrations.set(true)

        when:
        EmbeddedServer server = startService('eureka.client.registration.fail-fast': true)

        then: 'the failed registration stops the server, which de-registers the service'
        conditions.eventually {
            assert !server.running
            // the context stops right after the server, so the de-registration may not reach Eureka
            assert events.any { it.formattedMessage.contains('De-registered service') || it.formattedMessage.contains('Error occurred de-registering service') }
        }
        !events.any { it.formattedMessage.contains('blocking') }

        cleanup:
        logger.detachAppender(appender)
        server?.close()
    }

    private EmbeddedServer startService(Map<String, Object> config = [:]) {
        ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'            : 'test-eureka-re-registration',
                'eureka.client.host'                    : eureka.host,
                'eureka.client.port'                    : eureka.port,
                'eureka.client.discovery.enabled'       : false,
                'eureka.client.registration.retry-count': 1,
                'eureka.client.registration.retry-delay': '100ms',
                'micronaut.heartbeat.initial-delay'     : '1h',
                'micronaut.heartbeat.interval'          : '1h',
                'micronaut.health.monitor.enabled'      : false,
        ] + config)
    }

    private void awaitRegistration(EmbeddedServer server) {
        conditions.eventually {
            assert stub.registrations.get() == 1
            // the application has processed the registration response, so it reports heartbeats
            assert server.applicationContext.getBean(EurekaAutoRegistration).@registered.get()
        }
    }
}
