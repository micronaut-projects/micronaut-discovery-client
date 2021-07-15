/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.eureka.health

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.discovery.eureka.MockEurekaServer
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class EurekaHealthIndicatorSpec extends Specification {

    void "test eureka health indicator"() {
        given:
        Map eurekaServerMap = [
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED)             : true
        ]
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, eurekaServerMap, Environment.TEST)

        Map applicationContextMap = ['eureka.client.defaultZone': eurekaServer.getURL()]
        ApplicationContext applicationContext = ApplicationContext.run(applicationContextMap, Environment.TEST)

        EurekaHealthIndicator healthIndicator = applicationContext.getBean(EurekaHealthIndicator)

        when:
        HealthResult healthResult = Mono.from(healthIndicator.result).block()

        then:
        healthResult.status == HealthStatus.UP

        cleanup:
        eurekaServer?.stop()
    }
}
