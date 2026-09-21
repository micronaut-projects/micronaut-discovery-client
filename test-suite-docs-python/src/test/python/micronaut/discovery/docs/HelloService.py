# tag::imports[]
from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
# end::imports[]


# tag::class[]
@Singleton
class HelloService:

    http_client: Annotated[HttpClient, Inject, Client(id="hello-world")]

    def hello(self) -> str:
        return self.http_client.toBlocking().retrieve("/hello")
# end::class[]
