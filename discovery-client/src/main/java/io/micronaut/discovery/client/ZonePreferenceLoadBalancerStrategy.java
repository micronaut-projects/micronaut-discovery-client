/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.client;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.loadbalance.LoadBalancerStrategy;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link LoadBalancerStrategy} that prefers the instances in the zone of the application,
 * {@code micronaut.application.instance.zone}: it picks among them in turn, and among all the
 * available instances when none is in that zone or the application has no zone. Selected for a
 * service with {@code micronaut.http.services.<id>.load-balancer-strategy: zone-preference}.
 *
 * <p>The zone of an instance is {@link ServiceInstance#getZone()}: the {@code zone} metadata of an
 * instance registered with Consul or Eureka, or its availability zone on AWS for Eureka.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
@Prototype
@Named(ZonePreferenceLoadBalancerStrategy.NAME)
public final class ZonePreferenceLoadBalancerStrategy implements LoadBalancerStrategy {

    /**
     * The name of the strategy.
     */
    public static final String NAME = "zone-preference";

    private final @Nullable String zone;
    private final LoadBalancerStrategy delegate = LoadBalancerStrategy.of(LoadBalancerStrategy.ROUND_ROBIN);

    /**
     * @param applicationConfiguration The application configuration, which has the zone of the application
     */
    public ZonePreferenceLoadBalancerStrategy(ApplicationConfiguration applicationConfiguration) {
        this.zone = applicationConfiguration.getInstance().getZone().orElse(null);
    }

    @Override
    public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
        return delegate.select(sameZone(available), discriminator);
    }

    @Override
    public void report(ServiceInstance instance, LoadBalancer.Outcome outcome) {
        delegate.report(instance, outcome);
    }

    private List<ServiceInstance> sameZone(List<ServiceInstance> available) {
        if (zone == null) {
            return available;
        }
        List<ServiceInstance> sameZone = new ArrayList<>(available.size());
        for (ServiceInstance instance : available) {
            if (instance.getZone().filter(zone::equalsIgnoreCase).isPresent()) {
                sameZone.add(instance);
            }
        }
        return sameZone.isEmpty() ? available : sameZone;
    }
}
