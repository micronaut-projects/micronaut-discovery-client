package io.micronaut.discovery.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceList
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.client.LoadBalancerResolver
import io.micronaut.runtime.ApplicationConfiguration
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification

class ZonePreferenceLoadBalancerStrategySpec extends Specification {

    static final ServiceInstance A1 = instance('a1', 'zone-a')
    static final ServiceInstance A2 = instance('a2', 'zone-a')
    static final ServiceInstance B1 = instance('b1', 'zone-b')
    static final ServiceInstance NONE = ServiceInstance.of('none', URI.create('http://none:8080'))

    void "instances in the zone of the application are preferred"() {
        given:
        def strategy = strategy('zone-a')

        expect:
        (1..6).collect { strategy.select([B1, A1, NONE, A2], null) }.toSet() == [A1, A2] as Set
    }

    void "the zone is compared regardless of case"() {
        expect:
        strategy('ZONE-B').select([A1, B1, A2], null) == B1
    }

    void "all instances are used when none is in the zone of the application"() {
        given:
        def strategy = strategy('zone-c')

        expect:
        (1..6).collect { strategy.select([A1, B1, NONE], null) }.toSet() == [A1, B1, NONE] as Set
    }

    void "all instances are used when the application has no zone"() {
        given:
        def strategy = strategy(null)

        expect:
        (1..6).collect { strategy.select([A1, B1], null) }.toSet() == [A1, B1] as Set
    }

    void "the strategy is selected by name for a service"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'spec.name'                                          : 'ZonePreferenceLoadBalancerStrategySpec',
            'micronaut.application.instance.zone'                : 'zone-b',
            'micronaut.http.services.zoned.load-balancer-strategy': ZonePreferenceLoadBalancerStrategy.NAME
        ])
        LoadBalancer loadBalancer = context.getBean(LoadBalancerResolver).resolve('zoned').get()

        expect:
        (1..6).collect { Mono.from(loadBalancer.select()).block() }.toSet() == [B1] as Set

        cleanup:
        context.close()
    }

    private static ZonePreferenceLoadBalancerStrategy strategy(String zone) {
        def configuration = new ApplicationConfiguration()
        configuration.instance.zone = zone
        return new ZonePreferenceLoadBalancerStrategy(configuration)
    }

    private static ServiceInstance instance(String id, String zone) {
        return ServiceInstance.builder('zoned', URI.create("http://${id}:8080")).zone(zone).build()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ZonePreferenceLoadBalancerStrategySpec')
    static class ZonedServiceInstanceList implements ServiceInstanceList {
        @Override
        String getID() {
            return 'zoned'
        }

        @Override
        List<ServiceInstance> getInstances() {
            return [A1, B1, A2]
        }
    }
}
