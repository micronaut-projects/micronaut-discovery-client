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
package io.micronaut.discovery.eureka

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.naming.NameUtils
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import io.micronaut.health.HealthStatus
import io.micronaut.health.HeartbeatEvent
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.validation.ConstraintViolationException

/**
 * @author graemerocher
 * @since 1.0
 */
@Stepwise
class EurekaMockAutoRegistrationSpec extends Specification {

    void "test that an application can be registered and de-registered with Eureka hyphenated"() {
        given:
        Map eurekaServerConfig = [
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED): true
        ]
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, eurekaServerConfig, Environment.TEST)

        when: "An application is started and eureka configured"
        String serviceId = 'gr8crm-tag-service'
        Map applicationConfig = ['consul.client.enabled'        : false,
                                 "micronaut.caches.discoveryClient.enabled"  : false,
                                 'eureka.client.host'                        : eurekaServer.getHost(),
                                 'eureka.client.port'                        : eurekaServer.getPort(),
                                 'jackson.deserialization.UNWRAP_ROOT_VALUE' : true,
                                 'micronaut.application.name'                : serviceId]
        EmbeddedServer application1 = ApplicationContext.run(EmbeddedServer, applicationConfig, Environment.TEST)

        Map applicationConfig2 = new HashMap(applicationConfig)
        applicationConfig2.put('micronaut.application.name', 'gr8crm-notification-service')
        EmbeddedServer application2 = ApplicationContext.run(EmbeddedServer, applicationConfig2, Environment.TEST)

        EurekaClient eurekaClient = application1.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.5)

        then: "The application is registered"
        conditions.eventually {
            Flux.from(eurekaClient.applicationInfos).blockFirst().size() == 2
            Flux.from(eurekaClient.getApplicationVips(NameUtils.hyphenate(serviceId))).blockFirst().size() == 2
            Flux.from(eurekaClient.getInstances(NameUtils.hyphenate(serviceId))).blockFirst().size() == 1
            Flux.from(eurekaClient.getServiceIds()).blockFirst().contains(NameUtils.hyphenate(serviceId))
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
            instanceInfo.status == InstanceInfo.Status.UP
        }

        cleanup:
        application1?.stop()
        application2?.stop()
        eurekaServer?.stop()
    }


    void "test that an application can be registered and de-registered with Eureka"() {
        given:
        Map eurekaServerConfig = [
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED): true
        ]
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, eurekaServerConfig, Environment.TEST)

        when: "An application is started and eureka configured"
        String serviceId = 'myService'
        Map applicationConfig = ['consul.client.enabled'        : false,
                                 "micronaut.caches.discoveryClient.enabled"  : false,
                                 'eureka.client.host'                        : eurekaServer.getHost(),
                                 'eureka.client.port'                        : eurekaServer.getPort(),
                                 'jackson.deserialization.UNWRAP_ROOT_VALUE' : true,
                                 'micronaut.application.name'                : serviceId]
        EmbeddedServer application = ApplicationContext.run(EmbeddedServer, applicationConfig, Environment.TEST)

        EurekaClient eurekaClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.5)

        then: "The application is registered"
        conditions.eventually {
            Flux.from(eurekaClient.applicationInfos).blockFirst().size() == 1
            Flux.from(eurekaClient.getApplicationVips(NameUtils.hyphenate(serviceId))).blockFirst().size() == 1
            Flux.from(eurekaClient.getInstances(NameUtils.hyphenate(serviceId))).blockFirst().size() == 1
            Flux.from(eurekaClient.getServiceIds()).blockFirst().contains(NameUtils.hyphenate(serviceId))
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
            instanceInfo.status == InstanceInfo.Status.UP
        }

        when:'the application reports down'
        ServiceInstance instance = Mock(ServiceInstance)
        instance.getId() >> 'myService'
        application.applicationContext.publishEvent(new HeartbeatEvent(instance, HealthStatus.DOWN))

        then:"The status is reported as down"
        conditions.eventually {
            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
            instanceInfo.status == InstanceInfo.Status.DOWN
        }

        when:"it comes back up"
        application.applicationContext.publishEvent(new HeartbeatEvent(instance, HealthStatus.UP))

        then:"The status is reported as up"
        conditions.eventually {
            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
            instanceInfo.status == InstanceInfo.Status.UP
        }

        when:"test validation"
        eurekaClient.register("", null)

        then:"Invalid arguments thrown"
        thrown(ConstraintViolationException)

        when: "The application is stopped"
        application?.stop()

        then: "The application is de-registered"
        conditions.eventually {
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 0
        }

        cleanup:
        eurekaServer?.stop()
    }

    @Unroll
    void "test that an application can be registered and de-registered with Eureka with metadata"() {

        given:
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, [
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED): true
        ])

        def map = ['consul.client.enabled'              : false,
                   'eureka.client.host'                       : eurekaServer.getHost(),
                   'eureka.client.port'                       : eurekaServer.getPort(),
                   'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                   'micronaut.application.name'                : serviceId]

        for(entry in configuration) {
            map.put("eureka.client.registration.$entry.key".toString(), entry.value)
        }

        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                map
        )

        DiscoveryClient discoveryClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.5)

        expect: "The metadata is correct"
        conditions.eventually {
            Flux.from(discoveryClient.getInstances(serviceId)).blockFirst().size() == 1
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
            configuration.every {
                instanceInfo."$it.key" == it.value
            }
        }

        cleanup:
        application?.stop()
        eurekaServer?.stop()

        where:
        serviceId   | configuration
        'myService' | ['asgName':'test', vipAddress:'myVip', secureVipAddress:'mySecureVip', appGroupName:'myAppGroup', status:InstanceInfo.Status.STARTING]
        'myService' | [homePageUrl:'http://home', statusPageUrl:'http://status', healthCheckUrl:'http://health', secureHealthCheckUrl:'http://securehealth']
        'myService' | ['metadata':[foo:'bar']]
        'myService' | [port:9999, securePort:9998]
    }
}
