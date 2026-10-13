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
package io.micronaut.discovery.consul.registration;

import io.micronaut.core.order.Ordered;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.client.v1.ConsulCheck;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Allows beans to add, modify or remove the checks that {@link ConsulAutoRegistration} registers with a service.
 *
 * <p>Customizers are invoked in {@link Ordered order} every time the service is registered, including
 * re-registrations. The list passed to {@link #customize(ServiceInstance, String, List)} is mutable and already
 * contains the default TTL or HTTP check (if enabled) followed by the checks configured under
 * {@code consul.client.registration.checks}.</p>
 *
 * <p>The ID of the default TTL check must not be changed and the check must not be removed, since the TTL
 * heartbeat reports the health status of the application to that check.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@FunctionalInterface
public interface ConsulCheckCustomizer extends Ordered {

    /**
     * Customizes the checks registered with a service.
     *
     * @param instance  The service instance being registered
     * @param serviceId The ID the service is registered with in Consul
     * @param checks    The mutable list of checks to register
     */
    void customize(@NonNull ServiceInstance instance, @NonNull String serviceId, @NonNull List<ConsulCheck> checks);
}
