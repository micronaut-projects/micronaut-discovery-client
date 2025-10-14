package io.micronaut.discovery.consul.watch

import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import spock.lang.Specification

class PropertiesChangeHandlerSpec extends Specification {

    Environment environment = Mock()
    ApplicationEventPublisher<RefreshEvent> eventPublisher = Mock()

    PropertiesChangeHandler propertiesChangeHandler = new PropertiesChangeHandler(environment, eventPublisher)

    void "test that Context is updated and RefreshEvent is published"() {
        given:
        def capturedProperties = new ArrayList<PropertySource>()
        final Map<String, Object> previous = new HashMap<>()
        previous.put("key_1", "value_1")
        previous.put("key_2", null)
        previous.put("key_3", "null")
        previous.put("key_4", null)
        previous.put("key_to_delete", "foo")

        final Map<String, Object> next = new HashMap<>()
        next.put("key_1", "value_2")
        next.put("key_2", "null")
        next.put("key_3", null)
        next.put("key_4", null)
        next.put("key_added", "bar")

        final var propertySources = new ArrayList<PropertySource>()
        propertySources.add(PropertySource.of("consul-consul-watcher[test]", previous, 99))
        propertySources.add(PropertySource.of("consul-application", Map.of("key_a", "value_a"), 66))
        1 * environment.getPropertySources() >> propertySources

        when:
        propertiesChangeHandler.handleChanges("config/consul-watcher,test", previous, next)

        then:
        2 * environment.addPropertySource(_ as PropertySource) >> { arguments ->
            capturedProperties.add(arguments[0])
            return environment
        }
        final var propertySource = capturedProperties
                .stream()
                .filter(ps -> ps.getName() == "consul-consul-watcher[test]")
                .findFirst()
        propertySource.isPresent()
        with(propertySource.get()) { ps ->
            ps.get("key_1") == "value_2"
            ps.get("key_2") == "null"
            ps.get("key_3") == null
            ps.get("key_4") == null
                }

        1 * eventPublisher.publishEvent({
            it instanceof RefreshEvent
            def refreshEvent = it as RefreshEvent
            with(refreshEvent.getSource()) { source ->
                source.size() == 5
            // updated keys, with previous values
                source.get("key_1") == "value_1"
                source.get("key_2") == null
                source.get("key_3") == "null"
            // deleted key, with previous value
                source.get("key_to_delete") == "foo"
            // added key, with null value ?
                source.get("key_added") == null
            }
        })
    }

    void "test that nothing is done when no differences found"() {
        given:
        final Map<String, Object> previous = Map.of("key_1", "value_1")
        final Map<String, Object> next = Map.of("key_1", "value_1")

        when:
        propertiesChangeHandler.handleChanges("config/consul-watcher", previous, next)

        then:
        0 * environment._
        0 * eventPublisher._
    }

    void "test that keep going when type classes are different but numbers"() {
        given:
        final Map<String, Object> previous = Map.of("key_int", 1)
        final Map<String, Object> next = Map.of("key_int", 1.0)

        final var propertySources = new ArrayList<PropertySource>()
        propertySources.add(PropertySource.of("consul-application", Map.of("key_int", 1), 66))
        1 * environment.getPropertySources() >> propertySources

        when:
        propertiesChangeHandler.handleChanges("config/application", previous, next)

        then:
        1 * environment.addPropertySource({ it.get("key_int").toString() == "1.0" })
        1 * eventPublisher.publishEvent({
            it instanceof RefreshEvent
            def refreshEvent = it as RefreshEvent
            refreshEvent.getSource().get("key_int").toString() == "1"
        })
    }

}
