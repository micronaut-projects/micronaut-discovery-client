package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulCheck
import io.micronaut.discovery.consul.registration.ConsulCheckCustomizer
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "RedisCheckCustomizerTest")
// tag::class[]
@Singleton
class RedisCheckCustomizer : ConsulCheckCustomizer {

    override fun customize(instance: ServiceInstance, serviceId: String, checks: MutableList<ConsulCheck>) {
        checks += ConsulCheck().apply {
            id = "service:$serviceId:redis"
            name = "redis"
            tcp = "localhost:6379"
            interval = "10s"
        }
    }
}
// end::class[]
