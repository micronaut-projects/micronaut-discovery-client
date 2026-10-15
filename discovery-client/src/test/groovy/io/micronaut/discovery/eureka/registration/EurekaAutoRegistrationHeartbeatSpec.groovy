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

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceIdGenerator
import io.micronaut.discovery.eureka.EurekaConfiguration
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
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
 * Verifies how {@link EurekaAutoRegistration} reacts to the outcome of a heartbeat, without a Eureka server.
 */
class EurekaAutoRegistrationHeartbeatSpec extends Specification {

    private static final String SERVICE_NAME = 'test-eureka-heartbeat'

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'micronaut.application.name'     : SERVICE_NAME,
            'eureka.client.host'             : 'localhost',
            'eureka.client.port'             : 8761,
            'eureka.client.discovery.enabled': false,
            // the registration configuration reads the server port, which must not be random before the server starts
            'micronaut.server.port'          : 8080,
    ])

    ServiceInstance instance = ServiceInstance.of(SERVICE_NAME, new URL('http://localhost:8080'))
    int registrations = 0

    void 'a heartbeat answered with #description re-registers the service'() {
        given:
        EurekaAutoRegistration autoRegistration = autoRegistration(heartbeatResult)

        when:
        autoRegistration.pulsate(instance, HealthStatus.UP)

        then:
        registrations == 1

        where:
        description     | heartbeatResult
        'an empty body' | Mono.empty()
        'a 404'         | Mono.error(new HttpClientResponseException('Not Found', HttpResponse.notFound()))
    }

    void 'a heartbeat answered with #description does not re-register the service'() {
        given:
        EurekaAutoRegistration autoRegistration = autoRegistration(heartbeatResult)

        when:
        autoRegistration.pulsate(instance, HealthStatus.UP)

        then:
        registrations == 0

        where:
        description       | heartbeatResult
        'a 200'           | Mono.just(HttpStatus.OK)
        'a 500'           | Mono.error(new HttpClientResponseException('Internal Server Error', HttpResponse.serverError()))
        'a generic error' | Mono.error(new IOException('Connection refused'))
    }

    private EurekaAutoRegistration autoRegistration(Publisher<HttpStatus> heartbeatResult) {
        EurekaClient eurekaClient = Stub(EurekaClient) {
            heartbeat(_, _) >> heartbeatResult
            updateStatus(_, _, _ as InstanceInfo.Status) >> Mono.just(HttpStatus.OK)
            register(_, _ as InstanceInfo) >> {
                registrations++
                Mono.just(HttpStatus.NO_CONTENT)
            }
        }
        EurekaAutoRegistration autoRegistration = new EurekaAutoRegistration(
                context.environment,
                eurekaClient,
                context.getBean(EurekaConfiguration),
                context.getBean(HeartbeatConfiguration),
                context.getBean(ServiceInstanceIdGenerator))
        autoRegistration.@registered.set(true)
        autoRegistration
    }
}
