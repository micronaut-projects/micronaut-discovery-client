package io.micronaut.discovery.docs

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.ApplicationContextConfigurer
import io.micronaut.context.annotation.ContextConfigurer
import io.micronaut.core.io.socket.SocketUtils

/**
 * Binds the embedded server to a free port and registers that port as the fixed URL of the
 * `hello-world` service, so the examples resolve the service without a Consul agent.
 */
@ContextConfigurer
class HelloWorldServiceConfigurer : ApplicationContextConfigurer {

    override fun configure(builder: ApplicationContextBuilder) {
        val port = SocketUtils.findAvailableTcpPort()
        builder.properties(mapOf(
            "micronaut.server.port" to port,
            "micronaut.http.services.hello-world.url" to "http://localhost:$port"))
    }
}
