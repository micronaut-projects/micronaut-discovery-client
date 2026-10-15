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

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.eureka.registration.EurekaAutoRegistration
import io.micronaut.health.HeartbeatTask
import io.micronaut.http.client.ReactiveClientResultTransformer
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Verifies that a service re-registers with Eureka when Eureka no longer knows the instance and
 * answers the heartbeat with a 404. The Micronaut Reactor HTTP client turns a 404 response into an
 * empty publisher, so subclasses run with and without it.
 */
abstract class AbstractEurekaReRegistrationSpec extends Specification {

    /**
     * @return The number of {@link ReactiveClientResultTransformer} beans expected on the test classpath
     */
    abstract int expectedResultTransformers()

    void 'the service re-registers when Eureka answers a heartbeat with a 404'() {
        given:
        EmbeddedServer eureka = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                : EurekaReRegistrationStub.SPEC_NAME,
                'micronaut.heartbeat.enabled': false,
        ])
        EurekaReRegistrationStub stub = eureka.applicationContext.getBean(EurekaReRegistrationStub)
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name'      : 'test-eureka-re-registration',
                'eureka.client.host'              : eureka.host,
                'eureka.client.port'              : eureka.port,
                'eureka.client.discovery.enabled' : false,
                'eureka.client.registration.retry-count': 1,
                'eureka.client.registration.retry-delay': '100ms',
                'micronaut.heartbeat.initial-delay': '1h',
                'micronaut.heartbeat.interval'    : '1h',
                'micronaut.health.monitor.enabled': false,
        ])
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.2)

        expect:
        server.applicationContext.getBeansOfType(ReactiveClientResultTransformer).size() == expectedResultTransformers()

        and: 'the service is registered'
        conditions.eventually {
            assert stub.registrations.get() == 1
            // the application has processed the registration response, so it reports heartbeats
            assert server.applicationContext.getBean(EurekaAutoRegistration).@registered.get()
        }

        when: 'Eureka forgets the instance and a heartbeat is reported'
        stub.forget()
        server.applicationContext.getBean(HeartbeatTask).pulsate()

        then: 'the service is registered again'
        conditions.eventually {
            assert stub.heartbeats.get() >= 1
            assert stub.registrations.get() == 2
        }

        cleanup:
        server?.close()
        eureka?.close()
    }
}
