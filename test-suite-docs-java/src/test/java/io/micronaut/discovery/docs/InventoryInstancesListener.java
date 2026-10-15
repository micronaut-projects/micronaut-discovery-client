package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.discovery.ServiceInstancesChangedEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.util.List;
// end::imports[]

@Requires(property = "spec.name", value = "InventoryInstancesListenerTest")
// tag::class[]
@Singleton
public class InventoryInstancesListener {

    private volatile List<ServiceInstance> inventoryInstances = List.of();

    @EventListener
    void onChange(ServiceInstancesChangedEvent event) {
        if ("inventory".equals(event.getServiceId())) {
            inventoryInstances = event.getInstances();
        }
    }

    public List<ServiceInstance> getInventoryInstances() {
        return inventoryInstances;
    }
}
// end::class[]
