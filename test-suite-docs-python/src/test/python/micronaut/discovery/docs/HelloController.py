from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Get


# The hello-world service the examples discover.
@Requires(property="spec.name", value="HelloClientTest")
@Controller("/hello")
class HelloController:

    @Get
    def index(self) -> str:
        return "Hello World"
