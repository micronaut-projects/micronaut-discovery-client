from typing import Annotated

from jakarta.inject import Inject
from java.util import List
from micronaut.context.annotation import Property
from micronaut.context.event import ApplicationEventPublisher
from micronaut.discovery import ServiceInstance
from micronaut.discovery.consul.discovery import ServiceInstancesChangedEvent
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .InventoryInstancesListener import InventoryInstancesListener


@Property(name="spec.name", value="InventoryInstancesListenerTest")
@MicronautTest
class InventoryInstancesListenerTest:

    listener: Annotated[InventoryInstancesListener, Inject]
    event_publisher: Annotated[ApplicationEventPublisher[ServiceInstancesChangedEvent], Inject]

    @Test
    def the_listener_keeps_the_current_instances_of_the_inventory_service(self) -> None:
        billing = ServiceInstance.of("billing", "localhost", 8082)
        inventory = ServiceInstance.of("inventory", "localhost", 8081)

        self.event_publisher.publishEvent(ServiceInstancesChangedEvent("billing", List.of(), List.of(billing)))
        assert self.listener.inventory_instances == []

        self.event_publisher.publishEvent(ServiceInstancesChangedEvent("inventory", List.of(), List.of(inventory)))
        assert self.listener.inventory_instances == [inventory]

        self.event_publisher.publishEvent(ServiceInstancesChangedEvent("inventory", List.of(inventory), List.of()))
        assert self.listener.inventory_instances == []
