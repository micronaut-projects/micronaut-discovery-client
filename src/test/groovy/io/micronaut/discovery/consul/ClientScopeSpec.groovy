/*
 * Copyright 2018 original authors
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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.RxHttpClient
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.inject.Inject

/**
 * @author graemerocher
 * @since 1.0
 */
@Ignore
class ClientScopeSpec extends Specification {


    void "test that a client can be discovered using @Client scope"() {
        given:
        // a mock consul server
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer,[(MockConsulServer.ENABLED):true])

        EmbeddedServer messageServer = ApplicationContext.run(EmbeddedServer, [
                'consul.client.port': consulServer.port,
                'micronaut.application.name': 'messageService'
        ])

        MessageService messageClient = ApplicationContext.run(MessageService, [
                'consul.client.port': consulServer.port
        ])

        expect:
        messageClient.getMessage() == "Server ${messageServer.port}"



        cleanup:
        messageServer?.stop()
        consulServer?.stop()


    }

    @IgnoreIf({ !System.getenv('CONSUL_PORT') })
    void "test that a client can be discovered using @Client scope with Consul "() {
        given:
        def consulServer = [
                port:System.getenv('CONSUL_PORT')
        ]
        EmbeddedServer messageServer = ApplicationContext.run(EmbeddedServer, [
                'consul.client.port': consulServer.port,
                'micronaut.application.name': 'messageService'
        ])

        EmbeddedServer messageServer2 = ApplicationContext.run(EmbeddedServer, [
                'consul.client.port': consulServer.port,
                'micronaut.application.name': 'messageService'
        ])

        MessageService messageClient = ApplicationContext.run(MessageService, [
                'consul.client.port': consulServer.port
        ])


        expect: "Different servers are called for each invocation of getMessage()"
        messageClient.getMessage().startsWith("Server ")
        messageClient.getMessage() != messageClient.getMessage()


        cleanup:
        messageServer.stop()
        messageServer2.stop()
    }


    static class MessageService {
        @Inject @Client('messageService') RxHttpClient client

        String getMessage() {
            client.retrieve('/message/value').blockingFirst()
        }
    }

    @Controller
    static class MessageController {
        @Inject EmbeddedServer embeddedServer

        @Get('/value')
        String value() {
            return "Server ${embeddedServer.port}"
        }
    }


}
