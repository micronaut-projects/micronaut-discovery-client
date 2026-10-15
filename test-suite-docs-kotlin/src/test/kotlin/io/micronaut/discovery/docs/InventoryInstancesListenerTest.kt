package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.discovery.ServiceInstancesChangedEvent
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "InventoryInstancesListenerTest")
@MicronautTest
class InventoryInstancesListenerTest {

    @Inject
    lateinit var listener: InventoryInstancesListener

    @Inject
    lateinit var eventPublisher: ApplicationEventPublisher<ServiceInstancesChangedEvent>

    @Test
    fun theListenerKeepsTheCurrentInstancesOfTheInventoryService() {
        val billing = ServiceInstance.of("billing", "localhost", 8082)
        val inventory = ServiceInstance.of("inventory", "localhost", 8081)

        eventPublisher.publishEvent(ServiceInstancesChangedEvent("billing", emptyList(), listOf(billing)))
        assertTrue(listener.inventoryInstances.isEmpty())

        eventPublisher.publishEvent(ServiceInstancesChangedEvent("inventory", emptyList(), listOf(inventory)))
        assertEquals(listOf(inventory), listener.inventoryInstances)

        eventPublisher.publishEvent(ServiceInstancesChangedEvent("inventory", listOf(inventory), emptyList()))
        assertTrue(listener.inventoryInstances.isEmpty())
    }
}
