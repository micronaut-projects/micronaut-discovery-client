package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.discovery.ServiceInstancesChangedEvent;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "InventoryInstancesListenerTest")
@MicronautTest
class InventoryInstancesListenerTest {

    @Inject
    InventoryInstancesListener listener;

    @Inject
    ApplicationEventPublisher<ServiceInstancesChangedEvent> eventPublisher;

    @Test
    void theListenerKeepsTheCurrentInstancesOfTheInventoryService() {
        ServiceInstance billing = ServiceInstance.of("billing", "localhost", 8082);
        ServiceInstance inventory = ServiceInstance.of("inventory", "localhost", 8081);

        eventPublisher.publishEvent(new ServiceInstancesChangedEvent("billing", List.of(), List.of(billing)));
        assertTrue(listener.getInventoryInstances().isEmpty());

        eventPublisher.publishEvent(new ServiceInstancesChangedEvent("inventory", List.of(), List.of(inventory)));
        assertEquals(List.of(inventory), listener.getInventoryInstances());

        eventPublisher.publishEvent(new ServiceInstancesChangedEvent("inventory", List.of(inventory), List.of()));
        assertTrue(listener.getInventoryInstances().isEmpty());
    }
}
