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
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.discovery.consul.client.v1.ConsulCheckStatus
import io.micronaut.discovery.consul.registration.ConsulCheckConfiguration
import io.micronaut.http.HttpMethod
import spock.lang.Specification

import java.time.Duration

class ConsulCheckConfigurationSpec extends Specification {

    void 'no additional checks are configured by default'() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBeansOfType(ConsulCheckConfiguration).isEmpty()

        cleanup:
        ctx.close()
    }

    void 'additional checks bind from YAML'() {
        given:
        ApplicationContext ctx = ApplicationContext.builder()
                .propertySources(PropertySource.of('test', yaml()))
                .start()

        when:
        Map<String, ConsulCheckConfiguration> checks = ctx.getBeansOfType(ConsulCheckConfiguration)
                .collectEntries { [(it.name): it] }

        then:
        checks.keySet() == ['database', 'readiness', 'grpc-service', 'batch', 'disabled'] as Set

        when:
        ConsulCheckConfiguration database = checks.database

        then:
        database.enabled
        database.tcp.get() == 'localhost:5432'
        database.interval == Duration.ofSeconds(5)
        database.timeout.get() == Duration.ofSeconds(1)
        database.id.get() == 'db-check'
        database.notes.get() == 'The database is reachable'
        !database.http.present
        !database.grpc.present
        !database.ttl.present

        when:
        ConsulCheckConfiguration readiness = checks.readiness

        then:
        readiness.http.get() == '/health/readiness'
        readiness.method.get() == HttpMethod.POST
        readiness.headers == ['X-Api-Key': ['secret']]
        readiness.tlsSkipVerify.get()
        readiness.deregisterCriticalServiceAfter.get() == Duration.ofMinutes(10)
        readiness.interval == Duration.ofSeconds(ConsulCheckConfiguration.DEFAULT_INTERVAL_SECONDS)
        !readiness.id.present

        when:
        ConsulCheckConfiguration grpc = checks['grpc-service']

        then:
        grpc.grpc.get() == 'localhost:9090/my.Service'
        grpc.grpcUseTls.get()

        when:
        ConsulCheckConfiguration batch = checks.batch

        then:
        batch.ttl.get() == Duration.ofSeconds(30)
        batch.status.get() == ConsulCheckStatus.WARNING

        and:
        !checks.disabled.enabled

        cleanup:
        ctx.close()
    }

    void 'additional checks bind from properties'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'consul.client.registration.checks.database.tcp'     : 'localhost:5432',
                'consul.client.registration.checks.database.interval': '10s',
        ])

        when:
        ConsulCheckConfiguration check = ctx.getBean(ConsulCheckConfiguration)

        then:
        check.name == 'database'
        check.tcp.get() == 'localhost:5432'
        check.interval == Duration.ofSeconds(10)

        cleanup:
        ctx.close()
    }

    private static Map<String, Object> yaml() {
        new YamlPropertySourceLoader().read('test', '''
consul:
  client:
    registration:
      checks:
        database:
          id: db-check
          tcp: localhost:5432
          interval: 5s
          timeout: 1s
          notes: The database is reachable
        readiness:
          http: /health/readiness
          method: POST
          headers:
            X-Api-Key:
              - secret
          tls-skip-verify: true
          deregister-critical-service-after: 10m
        grpc-service:
          grpc: localhost:9090/my.Service
          grpc-use-tls: true
        batch:
          ttl: 30s
          status: warning
        disabled:
          enabled: false
          tcp: localhost:1
'''.bytes)
    }
}
