package test;

import io.micronaut.testresources.testcontainers.AbstractTestContainersProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConsulProvider extends AbstractTestContainersProvider<GenericContainer<?>> {

    @Override
    protected String getSimpleName() {
        return "consul";
    }

    @Override
    protected String getDefaultImageName() {
        return "consul:1.9.0";
    }

    @Override
    protected GenericContainer<?> createContainer(DockerImageName imageName, Map<String, Object> requestedProperties, Map<String, Object> testResourcesConfiguration) {
        return new GenericContainer<>(imageName).waitingFor(new LogMessageWaitStrategy().withRegEx(".*Node info in sync.*").withTimes(2)).withExposedPorts(8500);
    }

    @Override
    protected Optional<String> resolveProperty(String propertyName, GenericContainer<?> container) {
        if (propertyName.equals("consul.address")) {
            return Optional.of(container.getHost() + ":" + container.getMappedPort(8500));
        }
        return Optional.empty();
    }

    @Override
    public List<String> getResolvableProperties(Map<String, Collection<String>> propertyEntries, Map<String, Object> testResourcesConfig) {
        return Arrays.asList("consul.address");
    }
}
