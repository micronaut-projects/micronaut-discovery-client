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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulCheckStatus
import io.micronaut.discovery.consul.client.v1.ConsulClient

import io.micronaut.discovery.consul.client.v1.HealthEntry
import io.micronaut.discovery.consul.client.v1.NewServiceEntry
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
class ConsulMockAutoRegistrationSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED):true
    ])

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['micronaut.application.name'              : 'test-auto-reg',
             "micronaut.caches.discoveryClient.enabled": false,
             'consul.client.host'                     : 'localhost',
             'consul.client.port'                     : consulServer.getPort()]
    )

    @Shared
    ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)

    @Shared
    DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)

    void 'test mock server'() {
        when:
        def status = Flux.from(client.register(new NewServiceEntry("test-service"))).blockFirst()
        then:
        status
        Flux.from(client.services).blockFirst()
        Flux.from(discoveryClient.getInstances('test-service')).blockFirst()
        Flux.from(client.deregister('test-service')).blockFirst()
    }

    void 'test that the service is automatically registered with Consul'() {

        given:
        PollingConditions conditions = new PollingConditions(timeout: 3)

        expect:
        conditions.eventually {
            List<ServiceInstance> instances = Flux.from(discoveryClient.getInstances('test-auto-reg')).blockFirst()
            instances.size() == 1
            instances[0].id.contains('test-auto-reg')
            instances[0].port == embeddedServer.getPort()
            instances[0].host == embeddedServer.getHost()
        }
    }

    void 'test that the service is automatically de-registered with Consul'() {

        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name': serviceName,
                                                                               'consul.client.host'       : 'localhost',
                                                                               'consul.client.port'       : consulServer.port])

        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        conditions.eventually {
            List<ServiceInstance> instances = Flux.from(discoveryClient.getInstances(serviceName)).blockFirst()
            instances.size() == 1
            instances[0].id.contains(serviceName)
            instances[0].port == anotherServer.getPort()
            instances[0].host == anotherServer.getHost()
            // TTL check by default so now URL
            MockConsulServer.newEntries.get(serviceName).checks.size() == 1
            MockConsulServer.newEntries.get(serviceName).checks[0].getHttp() == null
            MockConsulServer.newEntries.get(serviceName).checks[0].getStatus() == 'passing'
        }

        when: "stopping the server"
        anotherServer.stop()

        then:
        conditions.eventually {
            List<ServiceInstance> instances = Flux.from(discoveryClient.getInstances(serviceName)).blockFirst()
            instances.size() == 0
            !instances.find { it.id == serviceName }
        }
    }

    void "test that a service can be registered with tags"() {
        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name'      : serviceName,
                                                                               'consul.client.registration.tags': ['foo', 'bar'],
                                                                               'consul.client.host'             : 'localhost',
                                                                               'consul.client.port'             : consulServer.port])

        PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
        conditions.eventually {
            List<HealthEntry> entry = Flux.from(client.getHealthyServices(serviceName)).blockFirst()
            entry.size() == 1
            entry[0].service.tags == ['foo', 'bar']
        }

        cleanup:
        anotherServer.stop()
    }

    void "test that a service can be registered with metadata"() {
        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name'      : serviceName,
                                                                               'consul.client.registration.meta': [foo:'bar',  key:'value'],
                                                                               'consul.client.host'             : 'localhost',
                                                                               'consul.client.port'             : consulServer.port])

        PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
        Thread.sleep(30000)
        conditions.eventually {
            List<HealthEntry> entry = Flux.from(client.getHealthyServices(serviceName)).blockFirst()
            entry.size() == 1
            entry[0].service.meta == [foo:'bar',  key:'value']
        }

        cleanup:
        anotherServer.stop()
    }

    void "test that a service can be registered with an HTTP health check"() {
        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name'            : serviceName,
                                                                               'consul.client.registration.check.http': true,
                                                                               'consul.client.registration.tags'      : ['foo', 'bar'],
                                                                               'consul.client.registration.meta'      : [foo:'bar',  key:'value'],
                                                                               'consul.client.host'                   : 'localhost',
                                                                               'consul.client.port'                   : consulServer.port])

        PollingConditions conditions = new PollingConditions(timeout: 3)
        String expectedCheckURI = "http://localhost:${anotherServer.port}/health"
        then:

        conditions.eventually {
            List<HealthEntry> entry = Flux.from(client.getHealthyServices(serviceName)).blockFirst()
            entry.size() == 1
            entry[0].service.tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).checks.size() == 1
            MockConsulServer.newEntries.get(serviceName).tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).meta == [foo:'bar',  key:'value']
            MockConsulServer.newEntries.get(serviceName).checks[0].getHttp() == new URL(expectedCheckURI)
            MockConsulServer.newEntries.get(serviceName).checks[0].getStatus() == ConsulCheckStatus.PASSING.toString()
        }

        cleanup:
        anotherServer.stop()
    }

    void "test that a service can be registered with an HTTP health check and deregisterCriticalServiceAfter"() {
        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name'                                      : serviceName,
                                                                               'consul.client.registration.check.http'                          : true,
                                                                               'consul.client.registration.check.deregisterCriticalServiceAfter': '90m',
                                                                               'consul.client.registration.tags'                                : ['foo', 'bar'],
                                                                               'consul.client.registration.meta'                                : [key: 'value'],
                                                                               'consul.client.host'                                             : 'localhost',
                                                                               'consul.client.port'                                             : consulServer.port])

        PollingConditions conditions = new PollingConditions(timeout: 3)
        String expectedCheckURI = "http://localhost:${anotherServer.port}/health"
        then:

        conditions.eventually {
            List<HealthEntry> entry = Flux.from(client.getHealthyServices(serviceName)).blockFirst()
            entry.size() == 1
            entry[0].service.tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).checks.size() == 1
            MockConsulServer.newEntries.get(serviceName).tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).meta == [key: 'value']
            MockConsulServer.newEntries.get(serviceName).checks[0].getHttp() == new URL(expectedCheckURI)
            MockConsulServer.newEntries.get(serviceName).checks[0].getDeregisterCriticalServiceAfter() == '90m'
            MockConsulServer.newEntries.get(serviceName).checks[0].getStatus() == ConsulCheckStatus.PASSING.toString()
        }

        cleanup:
        anotherServer.stop()
    }

    void "test that a service can be registered with an HTTP health check and tlsSkipVerify"() {
        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name'                     : serviceName,
                                                                               'consul.client.registration.check.http'         : true,
                                                                               'consul.client.registration.check.tlsSkipVerify': true,
                                                                               'consul.client.registration.tags'               : ['foo', 'bar'],
                                                                               'consul.client.registration.meta'               : [key: 'value'],
                                                                               'consul.client.host'                            : 'localhost',
                                                                               'consul.client.port'                            : consulServer.port])

        String expectedCheckURI = "http://localhost:${anotherServer.port}/health"
        PollingConditions conditions = new PollingConditions(timeout: 5)


        then:
        conditions.eventually {
            List<HealthEntry> entry = Flux.from(client.getHealthyServices(serviceName)).blockFirst()
            entry.size() == 1
            entry[0].service.tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).checks.size() == 1
            MockConsulServer.newEntries.get(serviceName).tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).meta == [key: 'value']
            MockConsulServer.newEntries.get(serviceName).checks[0].getHttp() == new URL(expectedCheckURI)
            MockConsulServer.newEntries.get(serviceName).checks[0].getTlsSkipVerify()
            MockConsulServer.newEntries.get(serviceName).checks[0].getStatus() == ConsulCheckStatus.PASSING.toString()
        }

        cleanup:
        anotherServer.stop()
    }

    void "test that a service can be registered with an HTTP health check and HTTP method"() {
        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name'              : serviceName,
                                                                               'consul.client.registration.check.http'  : true,
                                                                               'consul.client.registration.check.method': 'POST',
                                                                               'consul.client.registration.tags'        : ['foo', 'bar'],
                                                                               'consul.client.registration.meta'        : [key: 'value'],
                                                                               'consul.client.host'                     : 'localhost',
                                                                               'consul.client.port'                     : consulServer.port])

        PollingConditions conditions = new PollingConditions(timeout: 3)
        String expectedCheckURI = "http://localhost:${anotherServer.port}/health"
        then:

        conditions.eventually {
            List<HealthEntry> entry = Flux.from(client.getHealthyServices(serviceName)).blockFirst()
            entry.size() == 1
            entry[0].service.tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).checks.size() == 1
            MockConsulServer.newEntries.get(serviceName).tags == ['foo', 'bar']
            MockConsulServer.newEntries.get(serviceName).meta == [key: 'value']
            MockConsulServer.newEntries.get(serviceName).checks[0].getHttp() == new URL(expectedCheckURI)
            MockConsulServer.newEntries.get(serviceName).checks[0].getMethod() == HttpMethod.POST
            MockConsulServer.newEntries.get(serviceName).checks[0].status == 'passing'
        }

        cleanup:
        anotherServer.stop()
    }


    void "test that a asl token can be configured"() {
        when: "creating another server instance"
        def serviceName = 'another-server'
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, ['consul.client.aslToken': ['xxxxxxxxxxxx'],(MockConsulServer.ENABLED):true])

        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name': serviceName,
                                                                               'consul.client.aslToken'   : ['xxxxxxxxxxxx'],
                                                                               'consul.client.port'       : consulServer.getPort()])

        ConsulClient consulClient = anotherServer.applicationContext.getBean(ConsulClient)
        PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
        conditions.eventually {
            List<HealthEntry> entry = Flux.from(consulClient.getHealthyServices(serviceName)).blockFirst()
            entry.size() == 1
        }

        when: "A regular client tries to talk to consul without the token"
        HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient, consulServer.getURL())
        client.toBlocking().retrieve('/v1/agent/services')


        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN


        cleanup:
        anotherServer?.stop()
        consulServer?.stop()
    }
}
