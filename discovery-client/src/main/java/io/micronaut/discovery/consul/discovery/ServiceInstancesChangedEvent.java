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
package io.micronaut.discovery.consul.discovery;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.discovery.ServiceInstance;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Published when the watch of a Consul service observes that the instances of the service changed: an instance was
 * registered or deregistered, or its address, tags, metadata or health changed.
 *
 * <p>The event is published on a blocking executor thread after the cache serving
 * {@link io.micronaut.discovery.DiscoveryClient#getInstances(String)} has been updated.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
public final class ServiceInstancesChangedEvent extends ApplicationEvent {

    private final transient List<ServiceInstance> previousInstances;
    private final transient List<ServiceInstance> instances;

    /**
     * @param serviceId         The service ID
     * @param previousInstances The instances before the change
     * @param instances         The instances after the change
     */
    public ServiceInstancesChangedEvent(@NonNull String serviceId,
                                        @NonNull List<ServiceInstance> previousInstances,
                                        @NonNull List<ServiceInstance> instances) {
        super(serviceId);
        this.previousInstances = List.copyOf(previousInstances);
        this.instances = List.copyOf(instances);
    }

    @Override
    public @NonNull String getSource() {
        return (String) super.getSource();
    }

    /**
     * @return The ID of the service whose instances changed
     */
    public @NonNull String getServiceId() {
        return getSource();
    }

    /**
     * @return The instances before the change
     */
    public @NonNull List<ServiceInstance> getPreviousInstances() {
        return previousInstances;
    }

    /**
     * @return The current instances
     */
    public @NonNull List<ServiceInstance> getInstances() {
        return instances;
    }

    @Override
    public String toString() {
        return "ServiceInstancesChangedEvent{serviceId=" + getServiceId()
            + ", previousInstances=" + previousInstances.size()
            + ", instances=" + instances.size() + '}';
    }
}
