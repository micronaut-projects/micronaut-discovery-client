from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.discovery import ServiceInstance
from micronaut.discovery.consul.client.v1 import ConsulCheck
from micronaut.discovery.consul.registration import ConsulCheckCustomizer
# end::imports[]


@Requires(property="spec.name", value="RedisCheckCustomizerTest")
# tag::class[]
@Singleton
class RedisCheckCustomizer(ConsulCheckCustomizer):

    def customize(self, instance: ServiceInstance, service_id: str, checks: list[ConsulCheck]) -> None:
        check = ConsulCheck()
        check.setId(f"service:{service_id}:redis")
        check.setName("redis")
        check.setTcp("localhost:6379")
        check.setInterval("10s")
        checks.append(check)
# end::class[]
