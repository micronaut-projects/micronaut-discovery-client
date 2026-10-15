from typing import Annotated

from jakarta.inject import Inject
from java.util import ArrayList
from micronaut.context.annotation import Property
from micronaut.discovery import ServiceInstance
from micronaut.discovery.consul.registration import ConsulCheckCustomizer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .RedisCheckCustomizer import RedisCheckCustomizer


@Property(name="spec.name", value="RedisCheckCustomizerTest")
@MicronautTest(startApplication=False)
class RedisCheckCustomizerTest:

    customizer: Annotated[RedisCheckCustomizer, Inject]

    @Test
    def the_customizer_adds_the_redis_check(self) -> None:
        assert isinstance(self.customizer, ConsulCheckCustomizer)

        checks = ArrayList()
        self.customizer.customize(ServiceInstance.of("my-service", "localhost", 8080), "my-service:8080", checks)

        assert checks.size() == 1
        check = checks.get(0)
        assert check.getId() == "service:my-service:8080:redis"
        assert check.getName() == "redis"
        assert check.getTcp() == "localhost:6379"
        assert check.getInterval() == "10s"
