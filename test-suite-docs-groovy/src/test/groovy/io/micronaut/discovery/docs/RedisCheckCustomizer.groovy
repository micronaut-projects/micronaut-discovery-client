package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulCheck
import io.micronaut.discovery.consul.registration.ConsulCheckCustomizer
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "RedisCheckCustomizerSpec")
// tag::class[]
@Singleton
class RedisCheckCustomizer implements ConsulCheckCustomizer {

    @Override
    void customize(ServiceInstance instance, String serviceId, List<ConsulCheck> checks) {
        checks << new ConsulCheck(
                id: "service:${serviceId}:redis",
                name: "redis",
                tcp: "localhost:6379",
                interval: "10s")
    }
}
// end::class[]
