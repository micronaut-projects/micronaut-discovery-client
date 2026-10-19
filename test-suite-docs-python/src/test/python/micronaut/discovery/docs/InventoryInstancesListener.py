from micronaut.context.annotation import Requires

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.discovery import ServiceInstance
from micronaut.discovery.consul.discovery import ServiceInstancesChangedEvent
from micronaut.runtime.event.annotation import EventListener
# end::imports[]


@Requires(property="spec.name", value="InventoryInstancesListenerTest")
# tag::class[]
@Singleton
class InventoryInstancesListener:

    def __init__(self) -> None:
        self.inventory_instances: list[ServiceInstance] = []

    @EventListener
    def on_change(self, event: ServiceInstancesChangedEvent) -> None:
        if event.getServiceId() == "inventory":
            self.inventory_instances = list(event.getInstances())
# end::class[]
