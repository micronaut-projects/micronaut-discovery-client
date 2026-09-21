package io.micronaut.discovery.docsupport;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.core.io.socket.SocketUtils;

import java.util.Map;

/**
 * Binds the embedded server to a free port and registers that port as the fixed URL of the
 * {@code hello-world} service, so the Python examples resolve the service without a Consul agent.
 * A configurer is service-loaded before the GraalPy runtime exists, so it is a Java class.
 */
@ContextConfigurer
public class HelloWorldServiceConfigurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContextBuilder builder) {
        int port = SocketUtils.findAvailableTcpPort();
        builder.properties(Map.of(
                "micronaut.server.port", port,
                "micronaut.http.services.hello-world.url", "http://localhost:" + port));
    }
}
