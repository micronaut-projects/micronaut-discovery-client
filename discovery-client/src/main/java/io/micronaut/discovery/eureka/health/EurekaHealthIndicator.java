/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.eureka.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.eureka.client.v2.EurekaClient;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * A {@link HealthIndicator} for Eureka.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(classes = HealthIndicator.class)
@Requires(beans = EurekaClient.class)
public class EurekaHealthIndicator implements HealthIndicator {
    private final EurekaClient eurekaClient;

    /**
     * @param eurekaClient The Eureka client
     */
    public EurekaHealthIndicator(EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        Flux<List<String>> serviceIds = Flux.from(eurekaClient.getServiceIds());
        return serviceIds.map(ids -> {
            HealthResult.Builder builder = HealthResult.builder(EurekaClient.SERVICE_ID, HealthStatus.UP);
            return builder.details(Collections.singletonMap("available-services", ids)).build();
        }).onErrorResume(throwable -> {
            HealthResult.Builder builder = HealthResult.builder(EurekaClient.SERVICE_ID, HealthStatus.DOWN);
            builder.exception(throwable);
            return Flux.just(builder.build());
        });
    }
}
