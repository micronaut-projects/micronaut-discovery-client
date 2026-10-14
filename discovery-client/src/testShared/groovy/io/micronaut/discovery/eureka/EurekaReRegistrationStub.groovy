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
package io.micronaut.discovery.eureka

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * A minimal Eureka server that can forget the registered instance. Eureka answers a heartbeat
 * or a status update for an unknown instance with a 404.
 */
@Requires(property = 'spec.name', value = EurekaReRegistrationStub.SPEC_NAME)
@Controller('/eureka/apps/{appId}')
class EurekaReRegistrationStub {

    static final String SPEC_NAME = 'EurekaReRegistrationSpec'

    final AtomicInteger registrations = new AtomicInteger()
    final AtomicInteger heartbeats = new AtomicInteger()
    final AtomicInteger statusUpdates = new AtomicInteger()
    final AtomicReference<String> status = new AtomicReference<>()
    final AtomicReference<String> registeredStatus = new AtomicReference<>()
    final AtomicBoolean registered = new AtomicBoolean()
    final AtomicBoolean failRegistrations = new AtomicBoolean()

    @Post
    HttpResponse<?> register(String appId, @Body String instance) {
        registrations.incrementAndGet()
        def matcher = instance =~ /"status"\s*:\s*"(\w+)"/
        registeredStatus.set(matcher.find() ? matcher.group(1) : null)
        if (failRegistrations.get()) {
            return HttpResponse.serverError()
        }
        registered.set(true)
        HttpResponse.noContent()
    }

    @Delete('/{instanceId}')
    HttpStatus deregister(String appId, String instanceId) {
        registered.set(false)
        HttpStatus.OK
    }

    @Put('/{instanceId}')
    HttpResponse<?> heartbeat(String appId, String instanceId) {
        heartbeats.incrementAndGet()
        registered.get() ? HttpResponse.ok() : HttpResponse.notFound()
    }

    @Put('/{instanceId}/status')
    HttpResponse<?> updateStatus(String appId, String instanceId, @QueryValue String value) {
        statusUpdates.incrementAndGet()
        if (!registered.get()) {
            return HttpResponse.notFound()
        }
        status.set(value)
        HttpResponse.ok()
    }

    /**
     * Forget the registered instance, as a restarted Eureka server would.
     */
    void forget() {
        registered.set(false)
    }
}
