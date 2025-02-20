package io.micronaut.discovery.consul.watch

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.env.PropertySourceReader
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.discovery.consul.client.v1.KeyValue
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueriesConsulClient
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.time.Duration

class WatcherSpec extends Specification {

    private static Logger CLASS_LOGGER = (Logger) LoggerFactory.getLogger(AbstractWatcher.class)

    BlockedQueriesConsulClient consulClient = Mock()
    BlockingQueriesConfiguration watchConfiguration = Mock()
    PropertiesChangeHandler propertiesChangeHandler = Mock()

    PropertySourceReader propertySourceReader = new YamlPropertySourceLoader()
    Base64.Encoder base64Encoder = Base64.getEncoder()

    Watcher watcher
    ListAppender<ILoggingEvent> listAppender

    def setup() {
        CLASS_LOGGER.setLevel(Level.INFO)
    }

    def cleanup() {
        if (listAppender != null) {
            CLASS_LOGGER.detachAppender(listAppender)
            listAppender.stop()
            listAppender = null
        }

        if (watcher != null && watcher.isWatching()) {
            watcher.stop()
        }
    }

    void "test that Configurations changes are published"() {
        given:
        def conditions = new AsyncConditions()

        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        def keyValue = new KeyValue(1234, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value".getBytes()))
        def newKeyValue = new KeyValue(4567, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value_2".getBytes()))

        1 * consulClient.watchValues("path/to/yaml", false, null) >> Mono.just(List.of(keyValue)) // init
        2 * consulClient.watchValues("path/to/yaml", false, 1234) >>> [
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(List.of(keyValue)), // no change
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(List.of(newKeyValue)) // change
        ]

        watchConfiguration.getDelayDuration() >> Duration.ZERO

        1 * propertiesChangeHandler.handleChanges(
                "path/to/yaml",
                {
                    Map<String, Object> previous ->
                        conditions.evaluate {
                            verifyAll {
                                previous.size() == 1
                                previous.get("foo.bar") == "value"
                            }
                        }
                },
                {
                    Map<String, Object> next ->
                        conditions.evaluate {
                            verifyAll {
                                next.size() == 1
                                next.get("foo.bar") == "value_2"
                            }
                        }
                })

        when:
        watcher.start()

        then:
        conditions.await(5)
    }

    void "test that Configuration handle null KV"() {
        given:
        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        when:
        def value = watcher.readValue(null)

        then:
        value.isEmpty()
    }

    void "test that Native changes are published"() {
        given:
        def conditions = new AsyncConditions()

        watcher = new NativeWatcher(List.of("path/to/"), consulClient, watchConfiguration, propertiesChangeHandler)

        def previousKeyValue1 = new KeyValue(12, "path/to/foo.bar", base64Encoder.encodeToString("value_a".getBytes()))
        def previousKeyValue2 = new KeyValue(34, "path/to/other.key", base64Encoder.encodeToString("value_b".getBytes()))
        def previousKvs = new ArrayList<>(List.of(previousKeyValue1, previousKeyValue2))

        def nextKeyValue1 = new KeyValue(56, "path/to/foo.bar", base64Encoder.encodeToString("value_c".getBytes()))
        def nextKeyValue2 = new KeyValue(78, "path/to/other.key", base64Encoder.encodeToString("value_b".getBytes()))
        def nextKvs = new ArrayList<>(List.of(nextKeyValue1, nextKeyValue2))

        1 * consulClient.watchValues("path/to/", true, null) >> Mono.just(previousKvs) // init
        2 * consulClient.watchValues("path/to/", true, 34) >>> [
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(previousKvs), // no change
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(nextKvs) // change
        ]

        _ * consulClient.watchValues("path/to/", true, 78) >> Mono.delay(Duration.ofSeconds(10))
                .thenReturn(nextKvs) // no change

        watchConfiguration.getDelayDuration() >> Duration.ZERO

        1 * propertiesChangeHandler.handleChanges(
                "path/to/",
                { previous ->
                    conditions.evaluate {
                        verifyAll {
                            previous.size() == 2
                            previous.get("foo.bar") == "value_a"
                            previous.get("other.key") == "value_b"
                        }
                    }
                },
                { next ->
                    conditions.evaluate {
                        verifyAll {
                            next.size() == 2
                            next.get("foo.bar") == "value_c"
                            next.get("other.key") == "value_b"
                        }
                    }
                })

        when:
        watcher.start()

        then:
        conditions.await(5)
    }

    void "test that Native handle null KV"() {
        given:
        watcher = new NativeWatcher(List.of("path/to/yaml"), consulClient, watchConfiguration, propertiesChangeHandler)

        when:
        def value = watcher.readValue(null)

        then:
        value.isEmpty()
    }

    void "test that global error are logged"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.ERROR)

        watcher = new ConfigurationsWatcher(List.of("path/to/global_error"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        def keyValue = new KeyValue(0, "path/to/global_error", "")
        def newKeyValue = new KeyValue(1, "path/to/global_error", "incorrect data")
        1 * consulClient.watchValues("path/to/global_error", false, null) >> Mono.just(List.of(keyValue)) // init
        1 * consulClient.watchValues("path/to/global_error", false, 0) >> Mono.just(List.of(newKeyValue)) // change

        watchConfiguration.getDelayDuration() >> Duration.ZERO

        when:
        watcher.start()

        then:
        Thread.sleep(500)
        listAppender.list.size() == 1
        def loggingEvent = listAppender.list.get(0)
        loggingEvent.getFormattedMessage() == "Watching kvPath=path/to/global_error failed"
        with((ThrowableProxy) loggingEvent.getThrowableProxy()) {
            def throwable = it.getThrowable()
            throwable instanceof IllegalArgumentException
            throwable.getMessage() == "Illegal base64 character 20"
        }

        0 * propertiesChangeHandler._
    }

    void "test that client NOT_FOUND errors are handled"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.TRACE)

        watcher = new ConfigurationsWatcher(List.of("path/to/NOT_FOUND"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        def exception = Mock(HttpClientResponseException)
        1 * exception.getStatus() >> HttpStatus.NOT_FOUND
        1 * consulClient.watchValues("path/to/NOT_FOUND", false, null) >> Mono.error(exception)
        watchConfiguration.getDelayDuration() >> Duration.ZERO

        when:
        watcher.start()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.TRACE == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "No KV found with kvPath=path/to/NOT_FOUND"
        ((ThrowableProxy) loggingEvent.getThrowableProxy()) == null

        and:
        0 * propertiesChangeHandler._
    }

    void "test that client http errors are handled"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.ERROR)

        watcher = new ConfigurationsWatcher(List.of("path/to/http_error"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        def exception = Mock(HttpClientResponseException)
        1 * exception.getStatus() >> HttpStatus.INTERNAL_SERVER_ERROR
        1 * consulClient.watchValues("path/to/http_error", false, null) >> Mono.error(exception)
        watchConfiguration.getDelayDuration() >> Duration.ZERO

        when:
        watcher.start()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.ERROR == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "Watching kvPath=path/to/http_error failed"
        ((ThrowableProxy) loggingEvent.getThrowableProxy()) != null
        ((ThrowableProxy) loggingEvent.getThrowableProxy()).getThrowable() == exception

        and:
        0 * propertiesChangeHandler._
    }

    void "test that client timeout errors are handled"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.WARN)

        watcher = new ConfigurationsWatcher(List.of("path/to/timeout"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        def exception = ReadTimeoutException.TIMEOUT_EXCEPTION
        2 * consulClient.watchValues("path/to/timeout", false, null) >> Mono.error(exception)
        watchConfiguration.getDelayDuration() >>> [Duration.ZERO, Duration.ofSeconds(5)]

        when:
        watcher.start()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.WARN == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "Timeout for kvPath=path/to/timeout"
        ((ThrowableProxy) loggingEvent.getThrowableProxy()) == null

        and:
        0 * propertiesChangeHandler._
    }

    void "test that other client errors are handled"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.ERROR)

        watcher = new ConfigurationsWatcher(List.of("path/to/error_other"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        def exception = new RuntimeException("boom")
        1 * consulClient.watchValues("path/to/error_other", false, null) >> Mono.error(exception)
        watchConfiguration.getDelayDuration() >> Duration.ZERO

        when:
        watcher.start()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.ERROR == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "Watching kvPath=path/to/error_other failed"
        ((ThrowableProxy) loggingEvent.getThrowableProxy()) != null
        ((ThrowableProxy) loggingEvent.getThrowableProxy()).getThrowable() == exception

        and:
        0 * propertiesChangeHandler._
    }

    void "test that an Exception is thrown when Watcher is already started"() {
        given:
        watcher = new ConfigurationsWatcher(List.of(), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        when:
        watcher.start()
        watcher.start()

        then:
        def caughtException = thrown(IllegalStateException)
        caughtException.message == "Watcher is already started"

        and:
        0 * consulClient._
        0 * watchConfiguration._
        0 * propertiesChangeHandler._
    }

    void "test that ERROR are logged and watcher is stopped when and exception occurs during start"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)

        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        def exception = new RuntimeException("boom")
        1 * consulClient.watchValues("path/to/yaml", false, null) >> { throw exception }
        watchConfiguration.getDelayDuration() >> Duration.ZERO

        when:
        watcher.start()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.ERROR == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "Error watching configurations: boom"
        with((ThrowableProxy) loggingEvent.getThrowableProxy()) {
            it.getThrowable() == exception
        }

        and:
        0 * propertiesChangeHandler._
    }

    void "test that WARN are logged when stopping a not started watcher"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.WARN)

        watcher = new ConfigurationsWatcher(List.of(), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)

        when:
        watcher.stop()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.WARN == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "You tried to stop an unstarted Watcher"

        and:
        0 * consulClient._
        0 * watchConfiguration._
        0 * propertiesChangeHandler._
    }

    void "test that stopping Watcher dispose all subscriptions"() {
        given:
        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, watchConfiguration, propertiesChangeHandler, propertySourceReader)
        def keyValue = new KeyValue(1234, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value".getBytes()))

        // fixme mock result to check the dispose call ?
        1 * consulClient.watchValues("path/to/yaml", false, null) >> Mono.delay(Duration.ofMillis(200))
                .thenReturn(List.of(keyValue))

        watchConfiguration.getDelayDuration() >> Duration.ZERO

        when:
        watcher.start()
        watcher.stop()

        then:
        0 * propertiesChangeHandler._
        // fixme check that Disposable#dispose() is called
    }
}
