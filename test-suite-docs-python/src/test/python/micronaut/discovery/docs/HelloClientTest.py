from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .HelloClient import HelloClient
from .HelloService import HelloService


@Property(name="spec.name", value="HelloClientTest")
@MicronautTest
class HelloClientTest:

    hello_client: Annotated[HelloClient, Inject]
    hello_service: Annotated[HelloService, Inject]

    @Test
    def the_declarative_client_resolves_the_hello_world_service(self) -> None:
        assert self.hello_client.hello() == "Hello World"

    @Test
    def the_injected_http_client_resolves_the_hello_world_service(self) -> None:
        assert self.hello_service.hello() == "Hello World"
