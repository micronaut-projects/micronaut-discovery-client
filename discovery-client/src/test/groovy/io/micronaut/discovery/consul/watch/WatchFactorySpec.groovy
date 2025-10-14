package io.micronaut.discovery.consul.watch

import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.discovery.consul.ConsulConfiguration
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueriesConsulClient
import spock.lang.Specification

import static io.micronaut.discovery.config.ConfigDiscoveryConfiguration.Format.*
import static io.micronaut.discovery.config.ConfigDiscoveryConfiguration.Format

class WatchFactorySpec extends Specification {

    Environment environment = Mock()
    BlockedQueriesConsulClient consulClient = Mock()
    BlockingQueriesConfiguration watchConfiguration = Mock()
    PropertiesChangeHandler propertiesChangeHandler = Mock()

    WatchFactory watchFactory = new WatchFactory(environment, consulClient, watchConfiguration, propertiesChangeHandler)

    void "test that all required KV paths are watched"() {
        given:
        def consulConfiguration = Mock(ConsulConfiguration)
        def discoveryConfiguration = Mock(ConsulConfiguration.ConsulConfigDiscoveryConfiguration)
        1 * consulConfiguration.getServiceId() >> Optional.of("my_application")
        1 * consulConfiguration.getConfiguration() >> discoveryConfiguration
        1 * discoveryConfiguration.getPath() >> Optional.of("path/to/config")
        1 * environment.getActiveNames() >> Set.of("cloud", "test")

        when:
        def kvPaths = watchFactory.computeKvPaths(consulConfiguration)

        then:
        kvPaths.size() == 6
        kvPaths.contains("path/to/config/application")
        kvPaths.contains("path/to/config/application,cloud")
        kvPaths.contains("path/to/config/application,test")
        kvPaths.contains("path/to/config/my_application")
        kvPaths.contains("path/to/config/my_application,cloud")
        kvPaths.contains("path/to/config/my_application,test")
    }

    void "test that an exception is thrown when the format is not supported"() {
        given:
        def consulConfiguration = Mock(ConsulConfiguration)
        def discoveryConfiguration = Mock(ConsulConfiguration.ConsulConfigDiscoveryConfiguration)
        1 * consulConfiguration.getServiceId() >> Optional.of("my_application")
        2 * consulConfiguration.getConfiguration() >> discoveryConfiguration
        1 * discoveryConfiguration.getPath() >> Optional.of("path/to/config")
        1 * discoveryConfiguration.getFormat() >> FILE
        1 * environment.getActiveNames() >> Set.of()

        when:
        watchFactory.createWatcher(consulConfiguration)

        then:
        thrown(ConfigurationException)
    }

    void "test than NATIVE format is supported"() {
        given:
        def consulConfiguration = Mock(ConsulConfiguration)
        def discoveryConfiguration = Mock(ConsulConfiguration.ConsulConfigDiscoveryConfiguration)
        1 * consulConfiguration.getServiceId() >> Optional.of("my_application")
        2 * consulConfiguration.getConfiguration() >> discoveryConfiguration
        1 * discoveryConfiguration.getPath() >> Optional.of("path/to/native")
        1 * discoveryConfiguration.getFormat() >> NATIVE
        1 * environment.getActiveNames() >> Set.of()

        when:
        def watcher = watchFactory.createWatcher(consulConfiguration)

        then:
        watcher instanceof NativeWatcher
        def kvPaths = ReflectionUtils.getFieldValue(NativeWatcher.class, "kvPaths", watcher)
        kvPaths.isPresent()
        kvPaths.get() == List.of("path/to/native/application/", "path/to/native/my_application/")
    }

    void "test than #format format is supported"(Format format) {
        given:
        def consulConfiguration = Mock(ConsulConfiguration)
        def discoveryConfiguration = Mock(ConsulConfiguration.ConsulConfigDiscoveryConfiguration)
        1 * consulConfiguration.getServiceId() >> Optional.of("my_application")
        2 * consulConfiguration.getConfiguration() >> discoveryConfiguration
        1 * discoveryConfiguration.getPath() >> Optional.of("path/to/" + format.name())
        1 * discoveryConfiguration.getFormat() >> format
        1 * environment.getActiveNames() >> Set.of()

        when:
        def watcher = watchFactory.createWatcher(consulConfiguration)

        then:
        watcher instanceof ConfigurationsWatcher
        def kvPaths = ReflectionUtils.getFieldValue(NativeWatcher.class, "kvPaths", watcher)
        kvPaths.isPresent()
        kvPaths.get() == List.of("path/to/" + format.name() + "/application", "path/to/" + format.name() + "/my_application")

        where:
        format     | _
        JSON       | _
        PROPERTIES | _
        YAML       | _
    }
}
