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
package io.micronaut.discovery.eureka.registration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.env.Environment
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceIdGenerator
import io.micronaut.discovery.eureka.EurekaConfiguration
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import io.micronaut.discovery.registration.AutoRegistration
import io.micronaut.health.HealthStatus
import io.micronaut.health.HeartbeatConfiguration
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verifies how {@link EurekaAutoRegistration} recovers when Eureka does not acknowledge a heartbeat
 * or a status update, as the Micronaut Reactor HTTP client reports a 404 response with an empty result.
 */
class EurekaAutoRegistrationRecoverySpec extends Specification {

    EurekaClient eurekaClient = Mock()

    EurekaConfiguration.EurekaRegistrationConfiguration registrationConfiguration = new EurekaConfiguration.EurekaRegistrationConfiguration(
            Stub(EmbeddedServer) {
                getHost() >> 'localhost'
                getPort() >> 8080
            },
            new ApplicationConfiguration(),
            null,
            'test-app',
            'localhost',
            8080,
            '127.0.0.1',
            'test-instance',
            null
    )

    EurekaAutoRegistration autoRegistration = new EurekaAutoRegistration(
            Stub(Environment),
            eurekaClient,
            new EurekaConfiguration(new EurekaConfiguration.EurekaConnectionPoolConfiguration(), new ApplicationConfiguration(), registrationConfiguration),
            new HeartbeatConfiguration(),
            Stub(ServiceInstanceIdGenerator)
    )

    ServiceInstance instance = Stub(ServiceInstance) {
        getId() >> 'test-app'
        getMetadata() >> ConvertibleValues.empty()
    }

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

    void 'a heartbeat Eureka does not acknowledge re-registers the instance as UP'() {
        given:
        registrationConfiguration.instanceInfo.status = InstanceInfo.Status.STARTING

        when:
        autoRegistration.pulsate(instance, HealthStatus.UP)

        then:
        1 * eurekaClient.heartbeat('test-app', 'test-instance') >> Mono.empty()
        1 * eurekaClient.register('test-app', { InstanceInfo info -> info.status == InstanceInfo.Status.UP }) >> Mono.just(HttpStatus.NO_CONTENT)
        1 * eurekaClient.updateStatus('test-app', 'test-instance', InstanceInfo.Status.UP) >> Mono.just(HttpStatus.OK)
        events.any { it.level == Level.WARN && it.formattedMessage.contains('did not acknowledge the heartbeat') }
        autoRegistration.@registered.get()
    }

    void 'a status update Eureka does not acknowledge re-registers the instance with the reported status'() {
        when:
        autoRegistration.pulsate(instance, HealthStatus.DOWN)

        then:
        0 * eurekaClient.heartbeat(*_)
        1 * eurekaClient.updateStatus('test-app', 'test-instance', InstanceInfo.Status.DOWN) >> Mono.empty()
        1 * eurekaClient.register('test-app', { InstanceInfo info -> info.status == InstanceInfo.Status.DOWN }) >> Mono.just(HttpStatus.NO_CONTENT)
        events.any { it.level == Level.WARN && it.formattedMessage.contains('did not acknowledge the status DOWN') }

        and: 'the status is reported again with the next heartbeat'
        autoRegistration.@lastStatus.get() == null
    }

    void 'a status update failing with a server error does not re-register the instance'() {
        when:
        autoRegistration.pulsate(instance, HealthStatus.DOWN)

        then:
        1 * eurekaClient.updateStatus('test-app', 'test-instance', InstanceInfo.Status.DOWN) >> Mono.error(new HttpClientResponseException('Internal Server Error', HttpResponse.serverError()))
        0 * eurekaClient.register(*_)
        events.any { it.level == Level.ERROR && it.formattedMessage.startsWith('Error reporting state to Eureka: ') }
        autoRegistration.@lastStatus.get() == HealthStatus.DOWN
    }

    void 'a heartbeat failing with a server error does not re-register the instance'() {
        when:
        autoRegistration.pulsate(instance, HealthStatus.UP)

        then:
        1 * eurekaClient.heartbeat('test-app', 'test-instance') >> Mono.error(new HttpClientResponseException('Internal Server Error', HttpResponse.serverError()))
        1 * eurekaClient.updateStatus('test-app', 'test-instance', InstanceInfo.Status.UP) >> Mono.just(HttpStatus.OK)
        0 * eurekaClient.register(*_)
        events.any { it.level == Level.ERROR && it.formattedMessage.startsWith('Error reporting passing state to Eureka: ') }
    }
}
