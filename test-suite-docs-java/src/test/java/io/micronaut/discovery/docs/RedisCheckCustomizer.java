package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.client.v1.ConsulCheck;
import io.micronaut.discovery.consul.registration.ConsulCheckCustomizer;
import jakarta.inject.Singleton;

import java.util.List;
// end::imports[]

@Requires(property = "spec.name", value = "RedisCheckCustomizerTest")
// tag::class[]
@Singleton
class RedisCheckCustomizer implements ConsulCheckCustomizer {

    @Override
    public void customize(ServiceInstance instance, String serviceId, List<ConsulCheck> checks) {
        ConsulCheck check = new ConsulCheck();
        check.setId("service:" + serviceId + ":redis");
        check.setName("redis");
        check.setTcp("localhost:6379");
        check.setInterval("10s");
        checks.add(check);
    }
}
// end::class[]
