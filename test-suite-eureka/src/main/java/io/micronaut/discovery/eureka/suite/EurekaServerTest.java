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
package io.micronaut.discovery.eureka.suite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.eureka.client.v2.ApplicationInfo;
import io.micronaut.discovery.eureka.client.v2.EurekaClient;
import io.micronaut.discovery.eureka.client.v2.InstanceInfo;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Registers an application with a real Eureka server.
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(120)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class EurekaServerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String APP_ID = "eureka-server-app";

    @Container
    static final GenericContainer<?> EUREKA = new GenericContainer<>("jhipster/jhipster-registry:latest")
        .withExposedPorts(8761)
        .waitingFor(Wait.forLogMessage("(?s).*Application 'jhipster-registry' is running!.*", 1)
            .withStartupTimeout(Duration.ofMinutes(3)));

    @Test
    void registerAndDiscover() {
        String zone = "http://admin:admin@" + EUREKA.getHost() + ":" + EUREKA.getMappedPort(8761);
        try (EmbeddedServer application = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "micronaut.application.name", APP_ID,
            "eureka.client.default-zone", zone,
            "eureka.client.read-timeout", "5s",
            "eureka.client.registration.retry-delay", "1s",
            "eureka.client.registration.retry-count", 3,
            "micronaut.caches.discovery-client.enabled", false
        ))) {
            EurekaClient client = application.getApplicationContext().getBean(EurekaClient.class);

            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                ApplicationInfo applicationInfo = Mono.from(client.getApplicationInfo(APP_ID)).block(TIMEOUT);
                assertNotNull(applicationInfo);
                assertEquals(APP_ID.toUpperCase(), applicationInfo.getName());
                assertEquals(1, applicationInfo.getInstances().size());
                InstanceInfo instance = applicationInfo.getInstances().get(0);
                assertEquals(application.getPort(), instance.getPort());
                assertEquals(InstanceInfo.Status.UP, instance.getStatus());

                InstanceInfo instanceInfo = Mono.from(client.getInstanceInfo(APP_ID, instance.getInstanceId())).block(TIMEOUT);
                assertNotNull(instanceInfo);
                assertEquals(instance.getInstanceId(), instanceInfo.getInstanceId());

                List<ApplicationInfo> applications = Mono.from(client.getApplicationInfos()).block(TIMEOUT);
                assertNotNull(applications);
                assertEquals(1, applications.stream().filter(a -> a.getName().equals(APP_ID.toUpperCase())).count());

                List<ServiceInstance> instances = Mono.from(client.getInstances(APP_ID)).block(TIMEOUT);
                assertNotNull(instances);
                assertEquals(1, instances.size());
                assertEquals(application.getPort(), instances.get(0).getPort());
            });
        }
    }
}
