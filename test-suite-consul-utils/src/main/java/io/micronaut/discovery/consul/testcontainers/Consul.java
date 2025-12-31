package io.micronaut.discovery.consul.testcontainers;

import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * @see <a href="https://testcontainers.com/modules/consul/">Testcontainers Consul</a>
 */
public class Consul {
    private static final String PROPERTY_CONSUL_CLIENT_HOST = "consul.client.host";
    private static final String PROPERTY_CONSUL_CLIENT_PORT = "consul.client.port";
    private static final String PROPERTY_CONSUL_CLIENT_DEFAULT_ZONE = "consul.client.default-zone";
    public static final int CONSUL_HTTP_PORT = 8500;
    private static final String IMAGE_NAME = "consul:1.9.0";
    private static ConsulContainer container;

    public static ConsulContainer getContainer() {
        if (container == null) {
            container = new ConsulContainer(DockerImageName.parse(IMAGE_NAME));
            container.waitingFor(new HttpWaitStrategy().forStatusCode(200).forPath("/v1/status/leader"));
            container.start();
            do {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } while(!container.isRunning());
            return container;
        } else {
            return container;
        }
    }

    public static Map<String, String> getProperties() {
        return getProperties(getContainer());
    }

    private static Map<String, String> getProperties(ConsulContainer container) {
        return Map.of(
            PROPERTY_CONSUL_CLIENT_HOST,container.getHost(),
            PROPERTY_CONSUL_CLIENT_PORT, container.getMappedPort(CONSUL_HTTP_PORT).toString(),
            PROPERTY_CONSUL_CLIENT_DEFAULT_ZONE, container.getHost() + ":" + container.getMappedPort(CONSUL_HTTP_PORT)
        );
    }
}
