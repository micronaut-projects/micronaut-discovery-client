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
package io.micronaut.discovery.consul.registration

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceIdGenerator
import io.micronaut.discovery.consul.ConsulConfiguration
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry
import io.micronaut.discovery.consul.client.v1.ConsulServiceEntry
import io.micronaut.health.HealthStatus
import io.micronaut.health.HeartbeatConfiguration
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Verifies how {@link ConsulAutoRegistration} reacts to the outcome of a TTL heartbeat, without a Consul server.
 */
class ConsulAutoRegistrationHeartbeatSpec extends Specification {

    private static final String SERVICE_NAME = 'test-heartbeat'

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'micronaut.application.name': SERVICE_NAME,
            'consul.client.host'        : 'localhost',
            'consul.client.port'        : 8500,
    ])

    ServiceInstance instance = ServiceInstance.of(SERVICE_NAME, new URL('http://localhost:8080'))
    List<ConsulNewServiceEntry> registrations = []
    int lookups = 0

    void 'an empty #status heartbeat result re-registers the service with a #expectedCheckStatus check'() {
        given:
        ConsulAutoRegistration autoRegistration = autoRegistration(Mono.empty())

        when:
        autoRegistration.pulsate(instance, status)

        then:
        registrations.size() == 1
        registrations[0].checks()*.status == [expectedCheckStatus]
        lookups == 0

        where:
        status            | expectedCheckStatus
        HealthStatus.UP   | 'passing'
        HealthStatus.DOWN | 'critical'
    }

    void 'an acknowledged heartbeat does not re-register the service'() {
        given:
        ConsulAutoRegistration autoRegistration = autoRegistration(Mono.just(HttpStatus.OK))

        when:
        autoRegistration.pulsate(instance, HealthStatus.UP)

        then:
        registrations.isEmpty()
        lookups == 0
    }

    void 'a #status heartbeat rejected for an unknown check (#description) re-registers the service with a #expectedCheckStatus check'() {
        given:
        ConsulAutoRegistration autoRegistration = autoRegistration(Mono.error(error))

        when:
        autoRegistration.pulsate(instance, status)

        then:
        registrations.size() == 1
        registrations[0].checks()*.status == [expectedCheckStatus]
        lookups == 0

        where:
        description                     | status            | error
        '404'                           | HealthStatus.UP   | new HttpClientResponseException('Not Found', HttpResponse.notFound())
        '404'                           | HealthStatus.DOWN | new HttpClientResponseException('Not Found', HttpResponse.notFound())
        '500 unknown check body'        | HealthStatus.UP   | serverError('Unknown check "service:test-heartbeat:8080"')
        '500 unknown check body'        | HealthStatus.DOWN | serverError('Unknown check "service:test-heartbeat:8080"')
        '500 without TTL body'          | HealthStatus.UP   | serverError('CheckID "service:test-heartbeat:8080" does not have associated TTL')
        '500 unknown check message'     | HealthStatus.UP   | new HttpClientResponseException('Unknown check', HttpResponse.serverError())
        'wrapped 404'                   | HealthStatus.UP   | new RuntimeException('wrapped', new HttpClientResponseException('Not Found', HttpResponse.notFound()))
        expectedCheckStatus = status == HealthStatus.UP ? 'passing' : 'critical'
    }

    void 'a heartbeat failing with #description re-registers the service when Consul no longer knows it'() {
        given:
        ConsulAutoRegistration autoRegistration = autoRegistration(Mono.error(error), Mono.just([:]))

        when:
        autoRegistration.pulsate(instance, HealthStatus.DOWN)

        then:
        lookups == 1
        registrations.size() == 1
        registrations[0].checks()*.status == ['critical']

        where:
        description             | error
        'an unrelated 500'      | serverError('rpc error making call: No cluster leader')
        'a 500 without a body'  | new HttpClientResponseException('Internal Server Error', HttpResponse.serverError())
        'a 503'                 | new HttpClientResponseException('Unavailable', HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE))
        'a generic error'       | new IOException('Connection refused')
    }

    void 'a failing heartbeat does not re-register the service when Consul still knows it'() {
        given:
        Map<String, ConsulServiceEntry> services = [(serviceId()): new ConsulServiceEntry(SERVICE_NAME, 'localhost', 8080, [], serviceId(), [:])]
        ConsulAutoRegistration autoRegistration = autoRegistration(Mono.error(new IOException('Connection refused')), Mono.just(services))

        when:
        autoRegistration.pulsate(instance, HealthStatus.UP)

        then:
        lookups == 1
        registrations.isEmpty()
    }

    void 'a failing registration lookup after a failing heartbeat is tolerated'() {
        given:
        ConsulAutoRegistration autoRegistration = autoRegistration(
                Mono.error(new IOException('Connection refused')),
                Mono.error(new IOException('Still refused')))

        when:
        autoRegistration.pulsate(instance, HealthStatus.UP)

        then:
        noExceptionThrown()
        lookups == 1
        registrations.isEmpty()
    }

    private ConsulAutoRegistration autoRegistration(Publisher<HttpStatus> heartbeatResult,
                                                    Publisher<Map<String, ConsulServiceEntry>> services = Mono.error(new IllegalStateException('unexpected lookup'))) {
        ConsulClient consulClient = Stub(ConsulClient) {
            pass(*_) >> heartbeatResult
            fail(*_) >> heartbeatResult
            findServices() >> { lookups++; services }
            register(_ as ConsulNewServiceEntry) >> { ConsulNewServiceEntry entry ->
                registrations << entry
                Mono.just(HttpStatus.OK)
            }
        }
        ConsulAutoRegistration autoRegistration = new ConsulAutoRegistration(
                context.environment,
                consulClient,
                context.getBean(HeartbeatConfiguration),
                context.getBean(ConsulConfiguration),
                context.getBean(ServiceInstanceIdGenerator))
        autoRegistration.@registered.set(true)
        autoRegistration
    }

    private String serviceId() {
        context.getBean(ServiceInstanceIdGenerator).generateId(context.environment, instance)
    }

    private static HttpClientResponseException serverError(String body) {
        new HttpClientResponseException('Internal Server Error', HttpResponse.serverError().body(body))
    }
}
