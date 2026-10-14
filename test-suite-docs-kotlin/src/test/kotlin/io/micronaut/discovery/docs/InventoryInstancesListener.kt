package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.discovery.ServiceInstancesChangedEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "InventoryInstancesListenerTest")
// tag::class[]
@Singleton
open class InventoryInstancesListener {

    @Volatile
    var inventoryInstances: List<ServiceInstance> = emptyList()
        private set

    @EventListener
    open fun onChange(event: ServiceInstancesChangedEvent) {
        if (event.serviceId == "inventory") {
            inventoryInstances = event.instances
        }
    }
}
// end::class[]
