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
import io.micronaut.context.annotation.Requires
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.consul.client.v1.ConsistencyMode
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry
import io.micronaut.discovery.consul.config.ConsulConfigurationClient
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verifies the query parameters sent to Consul for discovery and KV reads.
 */
class ConsulDiscoveryQuerySpec extends Specification {

    static final String SPEC_NAME = 'ConsulDiscoveryQuerySpec'

    @Shared
    @AutoCleanup
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])

    @Shared
    RecordingConsul recorder = consulServer.applicationContext.getBean(RecordingConsul)

    def setup() {
        recorder.requests.clear()
    }

    private ApplicationContext client(Map<String, Object> config) {
        ApplicationContext.run([
                'consul.client.host'                       : 'localhost',
                'consul.client.port'                       : consulServer.port,
                'consul.client.registration.enabled'       : false,
                'micronaut.caches.discovery-client.enabled': false
        ] + config)
    }

    private HttpRequest<?> discover(Map<String, Object> config, String serviceId) {
        ApplicationContext ctx = client(config)
        try {
            Flux.from(ctx.getBean(ConsulClient).getInstances(serviceId)).blockFirst()
            return recorder.requests.find { it.path.startsWith('/v1/health/service/') }
        } finally {
            ctx.close()
        }
    }

    void 'default discovery query sends no tag, filter or consistency flags'() {
        when:
        HttpRequest<?> request = discover([:], 'foo')

        then:
        request.path == '/v1/health/service/foo'
        request.parameters.getAll('tag').isEmpty()
        !request.parameters.contains('filter')
        !request.parameters.contains('consistent')
        !request.parameters.contains('stale')
    }

    void 'several query tags are sent as repeated tag parameters'() {
        when:
        HttpRequest<?> request = discover(['consul.client.discovery.query-tags.foo': ['primary', 'v2']], 'foo')

        then:
        request.parameters.getAll('tag') == ['primary', 'v2']
    }

    void 'default query tags apply to services without specific tags'() {
        when:
        HttpRequest<?> request = discover([
                'consul.client.discovery.default-query-tags': ['blue'],
                'consul.client.discovery.query-tags.other'  : ['green']
        ], 'foo')

        then:
        request.parameters.getAll('tag') == ['blue']

        when:
        recorder.requests.clear()
        request = discover([
                'consul.client.discovery.default-query-tags': ['blue'],
                'consul.client.discovery.query-tags.foo'    : ['green']
        ], 'foo')

        then:
        request.parameters.getAll('tag') == ['green']
    }

    void 'the legacy single tag per service still works'() {
        when:
        HttpRequest<?> request = discover([
                'consul.client.discovery.tags.foo'          : 'legacy',
                'consul.client.discovery.default-query-tags': ['blue']
        ], 'foo')

        then:
        request.parameters.getAll('tag') == ['legacy']
    }

    void 'a per service filter expression is sent as the filter parameter'() {
        when:
        HttpRequest<?> request = discover([
                'consul.client.discovery.filters.foo'   : 'Service.Meta.version == "2"',
                'consul.client.discovery.default-filter': 'Service.Meta.version == "1"'
        ], 'foo')

        then:
        request.parameters.get('filter') == 'Service.Meta.version == "2"'

        when:
        recorder.requests.clear()
        request = discover(['consul.client.discovery.default-filter': 'Service.Meta.version == "1"'], 'foo')

        then:
        request.parameters.get('filter') == 'Service.Meta.version == "1"'
    }

    void 'the consistency mode #mode is sent as a query flag on discovery reads'() {
        when:
        HttpRequest<?> request = discover(['consul.client.discovery.consistency-mode': mode], 'foo')

        then:
        request.parameters.contains('consistent') == consistent
        request.parameters.contains('stale') == stale

        where:
        mode         | consistent | stale
        'default'    | false      | false
        'consistent' | true       | false
        'stale'      | false      | true
    }

    void 'the passing flag is still honoured'() {
        when:
        HttpRequest<?> request = discover(['consul.client.discovery.passing': true], 'foo')

        then:
        request.parameters.get('passing') == 'true'
    }

    void 'KV reads can request a consistency mode'() {
        given:
        ApplicationContext ctx = client([:])

        when:
        Flux.from(ctx.getBean(ConsulClient).readValues('config/application', 'dc2', null, null, ConsistencyMode.STALE)).blockFirst()
        HttpRequest<?> request = recorder.requests.find { it.path.startsWith('/v1/kv/') }

        then:
        request.path == '/v1/kv/config/application'
        request.parameters.get('dc') == 'dc2'
        request.parameters.contains('stale')
        !request.parameters.contains('consistent')

        cleanup:
        ctx.close()
    }

    void 'the config client applies the configured KV consistency mode'() {
        given:
        ApplicationContext ctx = client([
                (ConfigurationClient.ENABLED)          : true,
                'consul.client.config.consistency-mode': 'consistent',
                'micronaut.application.name'           : 'myapp'
        ])

        when:
        recorder.requests.clear()
        Flux.from(ctx.getBean(ConsulConfigurationClient).getPropertySources(ctx.getEnvironment())).collectList().block()
        List<HttpRequest<?>> kvRequests = recorder.requests.findAll { it.path.startsWith('/v1/kv/') }

        then: 'both the common and the application specific paths are read consistently'
        kvRequests*.path as Set == ['/v1/kv/config/application', '/v1/kv/config/myapp'] as Set
        kvRequests.every { it.parameters.contains('consistent') && !it.parameters.contains('stale') }

        cleanup:
        ctx.close()
    }

    void 'ConsulOperations implementations that predate the new overloads fall back to the legacy queries'() {
        given: 'an implementation that only implements the original methods'
        MockConsulServer legacy = new MockConsulServer(null)
        Flux.from(legacy.register(new ConsulNewServiceEntry('inventory', 'localhost', 8080, ['primary', 'v2'], null, null, null))).blockFirst()

        expect: 'tags beyond the first are matched on the client'
        Flux.from(legacy.findHealthyServices('inventory', null, ['primary', 'v2'], null, null, ConsistencyMode.STALE)).blockFirst().size() == 1
        Flux.from(legacy.findHealthyServices('inventory', null, ['primary', 'v3'], null, null, null)).blockFirst().isEmpty()
        Flux.from(legacy.readValues('config', null, null, null, ConsistencyMode.CONSISTENT)).blockFirst().isEmpty()

        when: 'a filter expression is requested'
        Flux.from(legacy.findHealthyServices('inventory', null, null, null, 'Service.Port == 8080', null)).blockFirst()

        then: 'it cannot be evaluated'
        thrown(UnsupportedOperationException)
    }

    @Controller('/v1')
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class RecordingConsul {

        final List<HttpRequest<?>> requests = new CopyOnWriteArrayList<>()

        @Get('/health/service/{service}')
        List<Map<String, Object>> health(String service, HttpRequest<?> request) {
            requests << request
            return []
        }

        @Get('/kv/{+key}')
        List<Map<String, Object>> kv(String key, HttpRequest<?> request) {
            requests << request
            return []
        }
    }
}
