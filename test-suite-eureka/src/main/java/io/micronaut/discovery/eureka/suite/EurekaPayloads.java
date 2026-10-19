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

/**
 * JSON payloads as sent by a Eureka server.
 */
final class EurekaPayloads {

    /**
     * An instance as returned by the Eureka server (see micronaut-discovery-client#264).
     */
    static final String INSTANCE = """
        {
          "instanceId": "module1:8080",
          "hostName": "module1-001",
          "app": "MODULE1",
          "ipAddr": "172.20.0.5",
          "status": "UP",
          "overriddenStatus": "UNKNOWN",
          "port": {"$": 8080, "@enabled": "true"},
          "securePort": {"$": 8443, "@enabled": "false"},
          "countryId": 1,
          "dataCenterInfo": {
            "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
            "name": "MyOwn"
          },
          "leaseInfo": {
            "renewalIntervalInSecs": 30,
            "durationInSecs": 90,
            "registrationTimestamp": 1700000000000,
            "lastRenewalTimestamp": 1700000030000,
            "evictionTimestamp": 0,
            "serviceUpTimestamp": 1700000000001
          },
          "metadata": {"management.port": "8080"},
          "homePageUrl": "http://module1-001:8080/",
          "statusPageUrl": "http://module1-001:8080/actuator/info",
          "healthCheckUrl": "http://module1-001:8080/actuator/health",
          "vipAddress": "module1",
          "secureVipAddress": "module1-secure",
          "isCoordinatingDiscoveryServer": "false",
          "lastUpdatedTimestamp": "1700000000002",
          "lastDirtyTimestamp": "1700000000003",
          "actionType": "ADDED"
        }
        """;

    /**
     * An instance running on AWS as returned by the Eureka server.
     */
    static final String AMAZON_INSTANCE = """
        {
          "hostName": "ec2-1-2-3-4.compute-1.amazonaws.com",
          "app": "MODULE2",
          "ipAddr": "10.0.0.1",
          "status": "UP",
          "port": {"$": 8080, "@enabled": "true"},
          "securePort": {"$": 443, "@enabled": "false"},
          "countryId": 1,
          "dataCenterInfo": {
            "@class": "com.netflix.appinfo.AmazonInfo",
            "name": "Amazon",
            "metadata": {
              "instance-id": "i-0123456789",
              "availability-zone": "us-east-1a"
            }
          },
          "vipAddress": "module2",
          "secureVipAddress": "module2"
        }
        """;

    /**
     * An application as returned by {@code GET /eureka/apps/MODULE1}, without the root {@code application} member.
     */
    static final String APPLICATION = "{\"name\":\"MODULE1\",\"instance\":[" + INSTANCE + "]}";

    /**
     * An application whose single instance is not in an array, as sent by older Eureka servers.
     */
    static final String APPLICATION_SINGLE_INSTANCE = "{\"name\":\"MODULE1\",\"instance\":" + INSTANCE + "}";

    private EurekaPayloads() {
    }
}
