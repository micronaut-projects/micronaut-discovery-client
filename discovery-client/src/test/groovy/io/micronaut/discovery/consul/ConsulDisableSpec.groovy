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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.health.ConsulHealthIndicator
import io.micronaut.discovery.consul.registration.ConsulAutoRegistration
import io.micronaut.discovery.consul.config.ConsulConfigurationClient
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

class ConsulDisableSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
        (MockConsulServer.ENABLED): true,
        'spec.name': 'ConsulDisableSpec'
    ])

    @Shared
    ConsulRequestRecorder recorder = consulServer.applicationContext.getBean(ConsulRequestRecorder)

    def setup() {
        recorder.requests.clear()
    }

    void 'disabling the config client alone does not stop other Consul requests'() {
        when:
        EmbeddedServer app = startApp(['micronaut.config-client.enabled': false])
        callHealth(app)

        then:
        !app.applicationContext.containsBean(ConsulConfigurationClient)
        app.applicationContext.containsBean(ConsulClient)
        new PollingConditions(timeout: 10).eventually {
            assert recorder.requests.contains('PUT /v1/agent/service/register')
            assert recorder.requests.contains('GET /v1/status/leader')
        }

        cleanup:
        app?.close()
    }

    void 'consul.client.enabled=false disables every Consul bean'() {
        when:
        EmbeddedServer app = startApp(['consul.client.enabled': false])
        callHealth(app)

        then:
        !app.applicationContext.containsBean(ConsulClient)
        !app.applicationContext.containsBean(ConsulAutoRegistration)
        !app.applicationContext.containsBean(ConsulHealthIndicator)
        !app.applicationContext.containsBean(ConsulConfigurationClient)
        recorder.requests.isEmpty()

        cleanup:
        app?.close()
    }

    void 'registration, discovery and health-check toggles stop Consul requests while keeping the client'() {
        when:
        EmbeddedServer app = startApp([
            'consul.client.registration.enabled': false,
            'consul.client.discovery.enabled'   : false,
            'consul.client.health-check'        : false
        ])
        callHealth(app)

        then:
        app.applicationContext.containsBean(ConsulClient)
        !app.applicationContext.containsBean(ConsulHealthIndicator)
        recorder.requests.isEmpty()

        cleanup:
        app?.close()
    }

    private EmbeddedServer startApp(Map<String, Object> extra) {
        Map<String, Object> config = [
            'micronaut.application.name': 'consul-disable-app',
            'consul.client.host'        : 'localhost',
            'consul.client.port'        : consulServer.port,
            'endpoints.health.enabled'  : true,
            'endpoints.health.sensitive': false
        ]
        config.putAll(extra)
        return ApplicationContext.run(EmbeddedServer, config)
    }

    private static void callHealth(EmbeddedServer app) {
        HttpClient client = app.applicationContext.createBean(HttpClient, app.URL)
        try {
            client.toBlocking().exchange(HttpRequest.GET('/health'))
        } catch (HttpClientResponseException ignored) {
            // the overall status is not relevant here, only the Consul requests it causes
        } finally {
            client.close()
        }
    }

    @ServerFilter('/v1/**')
    @Requires(property = 'spec.name', value = 'ConsulDisableSpec')
    static class ConsulRequestRecorder {

        final List<String> requests = new CopyOnWriteArrayList<>()

        @RequestFilter
        void record(HttpRequest<?> request) {
            requests.add(request.methodName + ' ' + request.path)
        }
    }
}
