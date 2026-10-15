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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsistencyMode
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry
import io.micronaut.discovery.consul.client.v1.KeyValue
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

/**
 * Verifies tag, filter and consistency queries against a real Consul agent.
 */
@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class ConsulDiscoveryFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    GenericContainer consulContainer = new GenericContainer("consul:1.9.0").withExposedPorts(8500)

    @Shared
    @AutoCleanup
    ApplicationContext registrar

    def setupSpec() {
        consulContainer.start()
        registrar = ApplicationContext.run(config([:]))
        ConsulClient client = registrar.getBean(ConsulClient)
        register(client, 'inventory-1', 8081, ['primary', 'v1'], [version: '1'])
        register(client, 'inventory-2', 8082, ['primary', 'v2'], [version: '2'])
        register(client, 'inventory-3', 8083, ['secondary', 'v2'], [version: '2'])
        Flux.from(client.putValue('config/application/greeting', 'hello')).blockFirst()
    }

    private Map<String, Object> config(Map<String, Object> extra) {
        [
                'consul.client.host'                       : consulContainer.host,
                'consul.client.port'                       : consulContainer.getMappedPort(8500),
                'consul.client.registration.enabled'       : false,
                'micronaut.caches.discovery-client.enabled': false
        ] + extra
    }

    private static void register(ConsulClient client, String id, int port, List<String> tags, Map<String, String> meta) {
        Flux.from(client.register(new ConsulNewServiceEntry('inventory', '127.0.0.1', port, tags, id, meta, null))).blockFirst()
    }

    private List<Integer> ports(Map<String, Object> extra) {
        ApplicationContext ctx = ApplicationContext.run(config(extra))
        try {
            List<ServiceInstance> instances = Flux.from(ctx.getBean(ConsulClient).getInstances('inventory')).blockFirst()
            return instances*.port.sort()
        } finally {
            ctx.close()
        }
    }

    void 'without query options every instance is returned'() {
        expect:
        ports([:]) == [8081, 8082, 8083]
    }

    void 'several query tags only match instances carrying every tag'() {
        expect:
        ports(['consul.client.discovery.query-tags.inventory': ['primary', 'v2']]) == [8082]
        ports(['consul.client.discovery.query-tags.inventory': ['v2']]) == [8082, 8083]
    }

    void 'default query tags apply when the service has no specific tags'() {
        expect:
        ports(['consul.client.discovery.default-query-tags': ['primary']]) == [8081, 8082]
    }

    void 'a filter expression is evaluated by Consul'() {
        expect:
        ports(['consul.client.discovery.filters.inventory': 'Service.Meta.version == "2"']) == [8082, 8083]
        ports(['consul.client.discovery.default-filter': '"secondary" in Service.Tags']) == [8083]
        ports([
                'consul.client.discovery.query-tags.inventory': ['v2'],
                'consul.client.discovery.filters.inventory'   : 'Service.Meta.version == "2" and "primary" in Service.Tags'
        ]) == [8082]
    }

    void 'consistency modes are accepted by Consul for discovery and KV reads'() {
        expect:
        ports(['consul.client.discovery.consistency-mode': mode]) == [8081, 8082, 8083]

        when:
        List<KeyValue> values = Flux.from(registrar.getBean(ConsulClient)
                .readValues('config/application', null, null, null, ConsistencyMode.valueOf(mode.toUpperCase()))).blockFirst()

        then:
        values*.key == ['config/application/greeting']

        where:
        mode << ['default', 'consistent', 'stale']
    }
}
