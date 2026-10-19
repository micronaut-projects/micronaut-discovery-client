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
import io.micronaut.discovery.eureka.client.v2.AmazonInfo;
import io.micronaut.discovery.eureka.client.v2.ApplicationInfo;
import io.micronaut.discovery.eureka.client.v2.DataCenterInfo;
import io.micronaut.discovery.eureka.client.v2.InstanceInfo;
import io.micronaut.discovery.eureka.client.v2.LeaseInfo;
import io.micronaut.discovery.eureka.client.v2.MyDataCenterInfo;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks that the Eureka model reads and writes the JSON used by the Eureka server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("java:S5960") // This is a TCK. Assertions are ok.
class EurekaJsonTest {

    private ApplicationContext context;
    private JsonMapper jsonMapper;

    @BeforeAll
    void start() {
        context = ApplicationContext.run();
        jsonMapper = context.getBean(JsonMapper.class);
    }

    @AfterAll
    void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void readInstance() throws Exception {
        InstanceInfo instance = jsonMapper.readValue(EurekaPayloads.INSTANCE, InstanceInfo.class);

        assertInstance(instance);
    }

    @Test
    void readAmazonInstance() throws Exception {
        InstanceInfo instance = jsonMapper.readValue(EurekaPayloads.AMAZON_INSTANCE, InstanceInfo.class);

        AmazonInfo amazonInfo = assertInstanceOf(AmazonInfo.class, instance.getDataCenterInfo());
        assertEquals(DataCenterInfo.Name.Amazon, amazonInfo.getName());
        assertEquals("i-0123456789", amazonInfo.getId());
        assertEquals("us-east-1a", amazonInfo.get(AmazonInfo.MetaDataKey.availabilityZone));
        assertEquals("i-0123456789", instance.getId());
        assertEquals(8080, instance.getPort());
    }

    @Test
    void readApplication() throws Exception {
        ApplicationInfo application = jsonMapper.readValue(EurekaPayloads.APPLICATION, ApplicationInfo.class);

        assertEquals("MODULE1", application.getName());
        assertEquals(1, application.getInstances().size());
        assertInstance(application.getInstances().get(0));
    }

    @Test
    void readApplicationWithSingleInstance() throws Exception {
        ApplicationInfo application = jsonMapper.readValue(EurekaPayloads.APPLICATION_SINGLE_INSTANCE, ApplicationInfo.class);

        assertEquals("MODULE1", application.getName());
        assertEquals(1, application.getInstances().size());
        assertInstance(application.getInstances().get(0));
    }

    @Test
    void writeInstance() throws Exception {
        InstanceInfo instance = new InstanceInfo("localhost", 8080, "127.0.0.1", "test-app", "localhost:test-app:8080");
        instance.setMetadata(Map.of("zone", "z1"));
        instance.setLeaseInfo(LeaseInfo.Builder.newBuilder().setRenewalIntervalInSecs(10).setDurationInSecs(20).build());

        String json = jsonMapper.writeValueAsString(instance);

        JSONAssert.assertEquals("""
            {
              "instanceId": "localhost:test-app:8080",
              "hostName": "localhost",
              "app": "test-app",
              "ipAddr": "127.0.0.1",
              "status": "UP",
              "port": {"$": 8080, "@enabled": true},
              "securePort": {"$": 0, "@enabled": false},
              "countryId": 1,
              "dataCenterInfo": {
                "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                "name": "MyOwn"
              },
              "leaseInfo": {
                "renewalIntervalInSecs": 10,
                "durationInSecs": 20,
                "registrationTimestamp": 0,
                "lastRenewalTimestamp": 0,
                "evictionTimestamp": 0,
                "serviceUpTimestamp": 0
              },
              "metadata": {"zone": "z1"},
              "homePageUrl": "http://localhost:8080",
              "statusPageUrl": "http://localhost:8080/health",
              "healthCheckUrl": "http://localhost:8080/health",
              "secureHealthCheckUrl": "https://localhost/health",
              "vipAddress": "test-app",
              "secureVipAddress": "test-app"
            }
            """, json, JSONCompareMode.STRICT);
    }

    @Test
    void writeAmazonInstance() throws Exception {
        InstanceInfo instance = new InstanceInfo("localhost", 8080, "127.0.0.1", "test-app", "localhost:test-app:8080");
        instance.setDataCenterInfo(AmazonInfo.Builder.newBuilder()
            .addMetadata(AmazonInfo.MetaDataKey.instanceId, "i-0123456789")
            .build());

        String json = jsonMapper.writeValueAsString(instance);

        JSONAssert.assertEquals("""
            {
              "dataCenterInfo": {
                "@class": "com.netflix.appinfo.AmazonInfo",
                "name": "Amazon",
                "metadata": {"instance-id": "i-0123456789"}
              }
            }
            """, json, JSONCompareMode.LENIENT);
    }

    @Test
    void roundTripInstance() throws Exception {
        InstanceInfo instance = jsonMapper.readValue(EurekaPayloads.INSTANCE, InstanceInfo.class);

        InstanceInfo copy = jsonMapper.readValue(jsonMapper.writeValueAsString(instance), InstanceInfo.class);

        assertInstance(copy);
    }

    private static void assertInstance(InstanceInfo instance) {
        assertEquals("module1:8080", instance.getInstanceId());
        assertEquals("module1:8080", instance.getId());
        assertEquals("module1-001", instance.getHostName());
        assertEquals("MODULE1", instance.getApp());
        assertEquals("172.20.0.5", instance.getIpAddr());
        assertEquals(InstanceInfo.Status.UP, instance.getStatus());
        assertEquals(8080, instance.getPort());
        assertEquals(8443, instance.getSecurePort());
        assertEquals(1, instance.getCountryId());
        assertEquals("module1", instance.getVipAddress());
        assertEquals("module1-secure", instance.getSecureVipAddress());
        assertEquals("http://module1-001:8080/", instance.getHomePageUrl());
        assertEquals("http://module1-001:8080/actuator/info", instance.getStatusPageUrl());
        assertEquals("http://module1-001:8080/actuator/health", instance.getHealthCheckUrl());
        assertEquals(Map.of("management.port", "8080"), instance.getMetadata());

        MyDataCenterInfo dataCenterInfo = assertInstanceOf(MyDataCenterInfo.class, instance.getDataCenterInfo());
        assertEquals(DataCenterInfo.Name.MyOwn, dataCenterInfo.getName());

        LeaseInfo leaseInfo = instance.getLeaseInfo();
        assertNotNull(leaseInfo);
        assertEquals(30, leaseInfo.getRenewalIntervalInSecs());
        assertEquals(90, leaseInfo.getDurationInSecs());
        assertEquals(1700000000000L, leaseInfo.getRegistrationTimestamp());
        assertEquals(1700000030000L, leaseInfo.getRenewalTimestamp());
        assertEquals(0L, leaseInfo.getEvictionTimestamp());
        assertEquals(1700000000001L, leaseInfo.getServiceUpTimestamp());
        assertFalse(instance.getMetadata().containsKey("@class"));
    }
}
