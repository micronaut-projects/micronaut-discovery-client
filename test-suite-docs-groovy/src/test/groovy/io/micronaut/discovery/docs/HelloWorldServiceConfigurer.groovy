package io.micronaut.discovery.docs

import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.ApplicationContextConfigurer
import io.micronaut.context.annotation.ContextConfigurer
import io.micronaut.core.io.socket.SocketUtils

/**
 * Binds the embedded server to a free port and registers that port as the fixed URL of the
 * {@code hello-world} service, so the examples resolve the service without a Consul agent.
 */
@ContextConfigurer
class HelloWorldServiceConfigurer implements ApplicationContextConfigurer {

    @Override
    void configure(ApplicationContextBuilder builder) {
        int port = SocketUtils.findAvailableTcpPort()
        builder.properties([
                "micronaut.server.port": port,
                "micronaut.http.services.hello-world.url": "http://localhost:" + port])
    }
}
