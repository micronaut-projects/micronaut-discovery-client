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
package io.micronaut.discovery.consul.discovery

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.ConsulNewServiceEntry
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Requires as SpockRequires
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies the Consul service instance watch against a real Consul agent.
 */
@SpockRequires({ DockerClientFactory.instance().isDockerAvailable() })
class ConsulServiceWatchSpec extends Specification {

    static final String SPEC_NAME = 'ConsulServiceWatchSpec'

    @Shared
    @AutoCleanup
    GenericContainer consulContainer = new GenericContainer("consul:1.9.0").withExposedPorts(8500)

    @Shared
    @AutoCleanup
    ApplicationContext registrar

    @Shared
    ConsulClient registrarClient

    PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.1)

    def setupSpec() {
        consulContainer.start()
        registrar = ApplicationContext.run(baseConfig())
        registrarClient = registrar.getBean(ConsulClient)
    }

    List<String> registered = []

    def cleanup() {
        registered.each { deregister(it) }
    }

    private Map<String, Object> baseConfig() {
        [
                'consul.client.host'                       : consulContainer.host,
                'consul.client.port'                       : consulContainer.getMappedPort(8500),
                'consul.client.registration.enabled'       : false,
                'micronaut.caches.discovery-client.enabled': false
        ]
    }

    private ApplicationContext watchingContext(Map<String, Object> extra = [:]) {
        ApplicationContext.run(baseConfig() + [
                'spec.name'                               : SPEC_NAME,
                'consul.client.discovery.watch.enabled'   : true,
                'consul.client.discovery.watch.wait'      : '20s',
                'consul.client.discovery.watch.retry-delay': '200ms',
                'consul.client.discovery.watch.max-retry-delay': '1s'
        ] + extra)
    }

    private void register(String service, String id, int port, List<String> tags = []) {
        Flux.from(registrarClient.register(new ConsulNewServiceEntry(service, '127.0.0.1', port, tags, id, null, null))).blockFirst()
        registered << id
    }

    private void deregister(String id) {
        Flux.from(registrarClient.deregister(id)).blockFirst()
    }

    private static List<Integer> ports(ApplicationContext ctx, String service) {
        List<ServiceInstance> instances = Flux.from(ctx.getBean(DiscoveryClient).getInstances(service)).blockFirst()
        instances*.port.sort()
    }

    void 'the watch is disabled by default'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(baseConfig())

        expect:
        !ctx.containsBean(ConsulServiceInstanceWatcher)
        !ctx.containsBean(ConsulServiceWatchConfiguration)

        cleanup:
        ctx.close()
    }

    void 'instances are served from the watch and changes publish events'() {
        given:
        register('inventory', 'inventory-1', 8081)
        register('inventory', 'inventory-2', 8082)
        ApplicationContext ctx = watchingContext()
        RequestRecorder recorder = ctx.getBean(RequestRecorder)
        ChangeListener listener = ctx.getBean(ChangeListener)
        ConsulServiceInstanceWatcher watcher = ctx.getBean(ConsulServiceInstanceWatcher)

        when: 'the first lookup warms the cache'
        List<Integer> first = ports(ctx, 'inventory')

        then:
        first == [8081, 8082]
        watcher.watchedServiceIds == ['inventory'] as Set
        conditions.eventually {
            assert recorder.blocking('inventory').size() >= 1
        }

        when: 'further lookups are served from the cache'
        int nonBlocking = recorder.nonBlocking('inventory').size()
        List<List<Integer>> repeated = (1..5).collect { ports(ctx, 'inventory') }

        then:
        nonBlocking == 1
        repeated.every { it == [8081, 8082] }
        recorder.nonBlocking('inventory').size() == 1
        listener.events.isEmpty()

        when: 'an instance is registered'
        register('inventory', 'inventory-3', 8083)

        then:
        conditions.eventually {
            assert listener.events.size() == 1
        }
        listener.events[0].serviceId == 'inventory'
        listener.events[0].previousInstances*.port.sort() == [8081, 8082]
        listener.events[0].instances*.port.sort() == [8081, 8082, 8083]
        ports(ctx, 'inventory') == [8081, 8082, 8083]
        recorder.nonBlocking('inventory').size() == 1

        when: 'an instance is deregistered'
        deregister('inventory-1')

        then:
        conditions.eventually {
            assert listener.events.size() == 2
        }
        listener.events[1].previousInstances*.port.sort() == [8081, 8082, 8083]
        listener.events[1].instances*.port.sort() == [8082, 8083]
        ports(ctx, 'inventory') == [8082, 8083]
        recorder.nonBlocking('inventory').size() == 1

        when: 'the context is closed'
        ctx.close()
        int requests = recorder.requests.size()
        register('inventory', 'inventory-4', 8084)
        sleep(1000)

        then: 'the watch is stopped'
        watcher.watchedServiceIds.isEmpty()
        recorder.requests.size() == requests
        listener.events.size() == 2

        cleanup:
        ctx?.close()
    }

    void 'the watch honours query tags and filters'() {
        given:
        register('catalog', 'catalog-1', 9081, ['primary'])
        register('catalog', 'catalog-2', 9082, ['secondary'])
        ApplicationContext ctx = watchingContext([
                'consul.client.discovery.query-tags.catalog': ['primary'],
                'consul.client.discovery.consistency-mode'  : 'stale',
                'consul.client.discovery.passing'           : true
        ])
        RequestRecorder recorder = ctx.getBean(RequestRecorder)
        ChangeListener listener = ctx.getBean(ChangeListener)

        expect:
        ports(ctx, 'catalog') == [9081]

        when: 'an instance without the tag is registered'
        register('catalog', 'catalog-3', 9083, ['secondary'])
        register('catalog', 'catalog-4', 9084, ['primary'])

        then:
        conditions.eventually {
            assert ports(ctx, 'catalog') == [9081, 9084]
        }
        listener.events.every { it.instances.every { i -> i.port in [9081, 9084] } }
        recorder.blocking('catalog').every {
            it.parameters.get('tag') == 'primary' && it.parameters.contains('stale') && it.parameters.contains('passing') && it.parameters.contains('wait')
        }
        recorder.nonBlocking('catalog').size() == 1

        cleanup:
        ctx.close()
    }

    void 'lookups fall back to direct queries while the watch is failing'() {
        given:
        register('billing', 'billing-1', 7081)
        ApplicationContext ctx = watchingContext()
        RequestRecorder recorder = ctx.getBean(RequestRecorder)

        expect:
        ports(ctx, 'billing') == [7081]
        conditions.eventually {
            assert recorder.blocking('billing').size() >= 1
        }

        when: 'blocking queries fail'
        recorder.failBlocking.set(true)
        register('billing', 'billing-2', 7082)

        then: 'lookups query Consul directly'
        conditions.eventually {
            assert !ctx.getBean(ConsulServiceInstanceWatcher).isWatching('billing')
        }
        ports(ctx, 'billing') == [7081, 7082]
        recorder.nonBlocking('billing').size() > 1

        when: 'blocking queries recover'
        recorder.failBlocking.set(false)
        register('billing', 'billing-3', 7083)

        then: 'the cache is warm again'
        conditions.eventually {
            assert ctx.getBean(ConsulServiceInstanceWatcher).isWatching('billing')
            assert ports(ctx, 'billing') == [7081, 7082, 7083]
        }
        int nonBlocking = recorder.nonBlocking('billing').size()
        ports(ctx, 'billing') == [7081, 7082, 7083]
        recorder.nonBlocking('billing').size() == nonBlocking

        cleanup:
        ctx.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class ChangeListener implements ApplicationEventListener<ServiceInstancesChangedEvent> {
        final List<ServiceInstancesChangedEvent> events = new CopyOnWriteArrayList<>()

        @Override
        void onApplicationEvent(ServiceInstancesChangedEvent event) {
            events.add(event)
        }
    }

    @Filter(patterns = '/v1/health/service/**', serviceId = ConsulClient.SERVICE_ID)
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class RequestRecorder implements HttpClientFilter {
        final List<MutableHttpRequest<?>> requests = new CopyOnWriteArrayList<>()
        final AtomicBoolean failBlocking = new AtomicBoolean()

        List<MutableHttpRequest<?>> blocking(String service) {
            requests.findAll { it.path.endsWith('/' + service) && it.parameters.contains('index') }
        }

        List<MutableHttpRequest<?>> nonBlocking(String service) {
            requests.findAll { it.path.endsWith('/' + service) && !it.parameters.contains('index') }
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            requests.add(request)
            if (failBlocking.get() && request.parameters.contains('index')) {
                return Publishers.just(new IllegalStateException('Simulated blocking query failure'))
            }
            return chain.proceed(request)
        }
    }
}
