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
import io.micronaut.discovery.eureka.client.v2.MyDataCenterInfo;
import io.micronaut.http.HttpStatus;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registers an application with a Eureka server stub and discovers it.
 */
@Timeout(120)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class EurekaRegistrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String APP_ID = "eureka-suite-app";

    @Test
    void registerAndDiscover() throws Exception {
        try (EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer.class, Map.of(EurekaStubServer.ENABLED, true))) {
            EurekaStubServer stub = eurekaServer.getApplicationContext().getBean(EurekaStubServer.class);
            EmbeddedServer application = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.application.name", APP_ID,
                "eureka.client.host", eurekaServer.getHost(),
                "eureka.client.port", eurekaServer.getPort(),
                "eureka.client.registration.retry-delay", "100ms",
                "eureka.client.registration.retry-count", 2,
                "micronaut.caches.discovery-client.enabled", false
            ));
            try {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                    assertEquals(List.of(), stub.getErrors());
                    assertEquals(1, stub.getInstances(APP_ID).size());
                });
                String instanceId = stub.getInstances(APP_ID).keySet().iterator().next();
                JSONAssert.assertEquals("""
                    {
                      "instance": {
                        "instanceId": "%s",
                        "app": "%s",
                        "status": "UP",
                        "port": {"$": %d, "@enabled": true},
                        "securePort": {"$": 0, "@enabled": false},
                        "dataCenterInfo": {
                          "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                          "name": "MyOwn"
                        },
                        "leaseInfo": {"renewalIntervalInSecs": 30, "durationInSecs": 90},
                        "vipAddress": "%s"
                      }
                    }
                    """.formatted(instanceId, APP_ID, application.getPort(), APP_ID),
                    stub.getRegistrations().get(0), JSONCompareMode.LENIENT);

                EurekaClient client = application.getApplicationContext().getBean(EurekaClient.class);

                List<ApplicationInfo> applications = Mono.from(client.getApplicationInfos()).block(TIMEOUT);
                assertNotNull(applications);
                assertEquals(1, applications.size());
                assertEquals(APP_ID.toUpperCase(), applications.get(0).getName());
                assertApplicationInstance(applications.get(0).getInstances().get(0), instanceId, application.getPort());

                assertEquals(List.of(APP_ID.toUpperCase()), Mono.from(client.getServiceIds()).block(TIMEOUT));

                List<ApplicationInfo> vips = Mono.from(client.getApplicationVips(APP_ID)).block(TIMEOUT);
                assertNotNull(vips);
                assertEquals(1, vips.size());

                ApplicationInfo applicationInfo = Mono.from(client.getApplicationInfo(APP_ID)).block(TIMEOUT);
                assertNotNull(applicationInfo);
                assertEquals(APP_ID.toUpperCase(), applicationInfo.getName());
                assertEquals(1, applicationInfo.getInstances().size());
                assertApplicationInstance(applicationInfo.getInstances().get(0), instanceId, application.getPort());

                InstanceInfo instanceInfo = Mono.from(client.getInstanceInfo(APP_ID, instanceId)).block(TIMEOUT);
                assertApplicationInstance(instanceInfo, instanceId, application.getPort());

                List<ServiceInstance> instances = Mono.from(client.getInstances(APP_ID)).block(TIMEOUT);
                assertNotNull(instances);
                assertEquals(1, instances.size());
                assertEquals(application.getPort(), instances.get(0).getPort());

                assertEquals(HttpStatus.OK, Mono.from(client.updateStatus(APP_ID, instanceId, InstanceInfo.Status.DOWN)).block(TIMEOUT));
                JsonNode status = stub.getInstances(APP_ID).get(instanceId).get("status");
                assertEquals("DOWN", status.getStringValue());
                assertEquals(HttpStatus.OK, Mono.from(client.heartbeat(APP_ID, instanceId)).block(TIMEOUT));
                assertTrue(stub.getHeartbeats().contains(APP_ID + "/" + instanceId));
            } finally {
                application.close();
            }

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(stub.getInstances(APP_ID).isEmpty()));
        }
    }

    @Test
    void registerExplicitly() throws Exception {
        try (EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer.class, Map.of(EurekaStubServer.ENABLED, true));
             ApplicationContext context = ApplicationContext.run(Map.of(
                 "eureka.client.host", eurekaServer.getHost(),
                 "eureka.client.port", eurekaServer.getPort(),
                 "eureka.client.registration.enabled", false,
                 "eureka.client.registration.retry-delay", "100ms",
                 "eureka.client.registration.retry-count", 2
             ))) {
            EurekaStubServer stub = eurekaServer.getApplicationContext().getBean(EurekaStubServer.class);
            EurekaClient client = context.getBean(EurekaClient.class);

            InstanceInfo instance = new InstanceInfo("localhost", 8080, "127.0.0.1", "explicit-app", "explicit-1");
            stub.failNextRegistrations(1);
            assertEquals(HttpStatus.NO_CONTENT, Mono.from(client.register("explicit-app", instance)).block(TIMEOUT));
            assertEquals(2, stub.getRegistrations().size());
            assertEquals(List.of(), stub.getErrors());

            InstanceInfo registered = Mono.from(client.getInstanceInfo("explicit-app", "explicit-1")).block(TIMEOUT);
            assertNotNull(registered);
            assertEquals("explicit-1", registered.getId());
            assertEquals(8080, registered.getPort());

            assertEquals(HttpStatus.OK, Mono.from(client.deregister("explicit-app", "explicit-1")).block(TIMEOUT));
            assertTrue(stub.getInstances("explicit-app").isEmpty());
        }
    }

    @Test
    void registerZoneInMetadata() throws Exception {
        try (EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer.class, Map.of(EurekaStubServer.ENABLED, true))) {
            EurekaStubServer stub = eurekaServer.getApplicationContext().getBean(EurekaStubServer.class);
            try (EmbeddedServer application = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.application.name", APP_ID,
                "micronaut.application.instance.zone", "zone-a",
                "eureka.client.registration.metadata.foo", "bar",
                "eureka.client.host", eurekaServer.getHost(),
                "eureka.client.port", eurekaServer.getPort(),
                "eureka.client.registration.retry-delay", "100ms",
                "eureka.client.registration.retry-count", 2,
                "micronaut.caches.discovery-client.enabled", false
            ))) {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                    assertEquals(List.of(), stub.getErrors());
                    assertEquals(1, stub.getInstances(APP_ID).size());
                });
                JSONAssert.assertEquals("""
                    {"instance": {"metadata": {"foo": "bar", "zone": "zone-a"}}}
                    """, stub.getRegistrations().get(0), JSONCompareMode.LENIENT);

                EurekaClient client = application.getApplicationContext().getBean(EurekaClient.class);
                List<ServiceInstance> instances = Mono.from(client.getInstances(APP_ID)).block(TIMEOUT);
                assertNotNull(instances);
                assertEquals(1, instances.size());
                assertEquals(Optional.of("zone-a"), instances.get(0).getZone());
            }
        }
    }

    private static void assertApplicationInstance(InstanceInfo instance, String instanceId, int port) {
        assertNotNull(instance);
        assertEquals(instanceId, instance.getInstanceId());
        assertEquals(APP_ID.toUpperCase(), instance.getApp());
        assertEquals(port, instance.getPort());
        assertEquals(InstanceInfo.Status.UP, instance.getStatus());
        assertInstanceOf(MyDataCenterInfo.class, instance.getDataCenterInfo());
        assertNotNull(instance.getLeaseInfo());
        assertEquals(30, instance.getLeaseInfo().getRenewalIntervalInSecs());
    }
}
