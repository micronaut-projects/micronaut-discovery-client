package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.discovery.ServiceInstancesChangedEvent
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "InventoryInstancesListenerSpec")
@MicronautTest
class InventoryInstancesListenerSpec extends Specification {

    @Inject
    InventoryInstancesListener listener

    @Inject
    ApplicationEventPublisher<ServiceInstancesChangedEvent> eventPublisher

    void "the listener keeps the current instances of the inventory service"() {
        given:
        ServiceInstance billing = ServiceInstance.of("billing", "localhost", 8082)
        ServiceInstance inventory = ServiceInstance.of("inventory", "localhost", 8081)

        when:
        eventPublisher.publishEvent(new ServiceInstancesChangedEvent("billing", [], [billing]))

        then:
        listener.inventoryInstances.empty

        when:
        eventPublisher.publishEvent(new ServiceInstancesChangedEvent("inventory", [], [inventory]))

        then:
        listener.inventoryInstances == [inventory]

        when:
        eventPublisher.publishEvent(new ServiceInstancesChangedEvent("inventory", [inventory], []))

        then:
        listener.inventoryInstances.empty
    }
}
