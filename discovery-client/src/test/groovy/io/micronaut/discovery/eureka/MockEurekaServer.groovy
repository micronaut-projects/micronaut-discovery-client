/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.eureka

import io.micronaut.context.annotation.Requires
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import io.micronaut.discovery.eureka.client.v2.MockApplicationInfo
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue

import java.util.concurrent.ConcurrentHashMap

/**
 * A mock Eureka server. Like the Eureka REST API, the bodies are wrapped in a member named after their type.
 *
 * @author graemerocher
 * @since 1.0
 */
@Controller(EurekaConfiguration.CONTEXT_PATH_PLACEHOLDER)
@Requires(property = MockEurekaServer.ENABLED)
class MockEurekaServer {
    public static Map<String, Map<String, Boolean>> heartbeats = new ConcurrentHashMap<>()
    public static Map<String, Map<String, InstanceInfo>> instances = new ConcurrentHashMap<>()
    public static final String ENABLED = 'enable.mock.eureka'

    @Post("/apps/{appId}")
    HttpStatus register(String appId, @Body("instance") InstanceInfo instance) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                 .put(instance.instanceId, instance)
        return HttpStatus.NO_CONTENT
    }

    @Delete("/apps/{appId}/{instanceId}")
    HttpStatus deregister(String appId, String instanceId) {
        def instances = instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>() })
        instances.remove(instanceId)
        return HttpStatus.OK
    }

    @Get("/apps/{appId}")
    Map<String, Object> getApplicationInfo(String appId) {
        List<InstanceInfo> infos = instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                 .values() as List<InstanceInfo>
        return [application: new MockApplicationInfo(appId, infos)]
    }

    @Get("/apps/{appId}/{instanceId}")
    HttpResponse<Map<String, Object>> getInstanceInfo(String appId, String instanceId) {
        InstanceInfo instance = instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()}).get(instanceId)
        return instance == null ? HttpResponse.notFound() : HttpResponse.ok([instance: instance] as Map<String, Object>)
    }

    @Get('/apps')
    Map<String, Object> getApplicationInfos() {
        return applications()
    }

    @Get('/vips/{vipAddress}')
    Map<String, Object> getApplicationVips(String vipAddress) {
        // this logic is wrong, i know.. we just test the call
        return applications()
    }

    @Put("/apps/{appId}/{instanceId}")
    HttpStatus heartbeat(String appId, String instanceId) {
        heartbeats.computeIfAbsent(appId, { String id -> [:]}).put(instanceId, true)
        return HttpStatus.OK
    }

    @Put("/apps/{appId}/{instanceId}/status")
    HttpStatus updateStatus(String appId, String instanceId, @QueryValue("value") InstanceInfo.Status status) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                .get(instanceId)?.status = status
        return HttpStatus.OK
    }

    private static Map<String, Object> applications() {
        List<MockApplicationInfo> applications = instances.findAll { !it.value.isEmpty() }.collect { it ->
            new MockApplicationInfo(it.key, it.value.values() as List<InstanceInfo>)
        }
        return [applications: [versions__delta: '1', application: applications]] as Map<String, Object>
    }
}
