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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A minimal Eureka server that can forget the registered instance. Eureka answers a heartbeat
 * for an unknown instance with a 404.
 */
@Requires(property = 'spec.name', value = EurekaReRegistrationStub.SPEC_NAME)
@Controller('/eureka/apps/{appId}')
class EurekaReRegistrationStub {

    static final String SPEC_NAME = 'EurekaReRegistrationSpec'

    final AtomicInteger registrations = new AtomicInteger()
    final AtomicInteger heartbeats = new AtomicInteger()
    final AtomicBoolean registered = new AtomicBoolean()

    @Post
    HttpStatus register(String appId, @Body String instance) {
        registered.set(true)
        registrations.incrementAndGet()
        HttpStatus.NO_CONTENT
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
    HttpStatus updateStatus(String appId, String instanceId) {
        HttpStatus.OK
    }

    /**
     * Forget the registered instance, as a restarted Eureka server would.
     */
    void forget() {
        registered.set(false)
    }
}
