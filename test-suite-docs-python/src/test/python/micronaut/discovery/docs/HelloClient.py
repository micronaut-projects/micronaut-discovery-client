# tag::imports[]
from abc import ABC, abstractmethod

from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
# end::imports[]


# tag::class[]
@Client(id="hello-world")
class HelloClient(ABC):

    @Get("/hello")
    @abstractmethod
    def hello(self) -> str:
        ...
# end::class[]
