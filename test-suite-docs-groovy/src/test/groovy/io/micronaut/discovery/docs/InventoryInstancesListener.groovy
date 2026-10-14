package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.discovery.ServiceInstancesChangedEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "InventoryInstancesListenerSpec")
// tag::class[]
@Singleton
class InventoryInstancesListener {

    private volatile List<ServiceInstance> inventoryInstances = []

    @EventListener
    void onChange(ServiceInstancesChangedEvent event) {
        if (event.serviceId == "inventory") {
            inventoryInstances = event.instances
        }
    }

    List<ServiceInstance> getInventoryInstances() {
        inventoryInstances
    }
}
// end::class[]
