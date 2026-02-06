package io.micronaut.discovery.consul.watch

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.env.PropertySourceReader
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.discovery.consul.client.v1.KeyValue
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueriesConsulClient
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration
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
    BlockingQueriesConfiguration blockingQueriesConfiguration = Mock()
    PropertiesChangeHandler propertiesChangeHandler = Mock()
    WatchConfiguration watchConfiguration = Mock()

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

        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def keyValue = new KeyValue(1234, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value".getBytes()))
        def newKeyValue = new KeyValue(4567, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value_2".getBytes()))

        1 * consulClient.watchValues("path/to/yaml", false, null) >> Mono.just(List.of(keyValue)) // init
        2 * consulClient.watchValues("path/to/yaml", false, 1234) >>> [
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(List.of(keyValue)), // no change
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(List.of(newKeyValue)) // change
        ]

        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO

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
        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        when:
        def value = watcher.readValue(null)

        then:
        value.isEmpty()
    }

    void "test that Native changes are published"() {
        given:
        def conditions = new AsyncConditions()

        watcher = new NativeWatcher(List.of("path/to/"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, watchConfiguration)

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

        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO

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
        watcher = new NativeWatcher(List.of("path/to/yaml"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, watchConfiguration)

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

        watcher = new ConfigurationsWatcher(List.of("path/to/global_error"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def keyValue = new KeyValue(0, "path/to/global_error", "")
        def newKeyValue = new KeyValue(1, "path/to/global_error", "incorrect data")
        1 * consulClient.watchValues("path/to/global_error", false, null) >> Mono.just(List.of(keyValue)) // init
        1 * consulClient.watchValues("path/to/global_error", false, 0) >> Mono.just(List.of(newKeyValue)) // change

        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO
        watchConfiguration.getMaxRetryAttempts() >> 0

        when:
        watcher.start()

        then:
        Thread.sleep(500)
        listAppender.list.size() == 1
        def loggingEvent = listAppender.list.get(0)
        loggingEvent.getFormattedMessage() == "Max retry attempts 0 reached, stopping watching path=path/to/global_error"
        with((ThrowableProxy) loggingEvent.getThrowableProxy()) {
            def throwable = it.getThrowable()
            throwable instanceof IllegalArgumentException
            throwable.getMessage() == "Illegal base64 character 20"
        }

        0 * propertiesChangeHandler._
        0 * watchConfiguration.getRetryDelayMs()
    }

    void "test that client NOT_FOUND errors are handled with configured Log Level"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.WARN)

        watcher = new ConfigurationsWatcher(List.of("path/to/NOT_FOUND"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def exception = Mock(HttpClientResponseException)
        1 * exception.getStatus() >> HttpStatus.NOT_FOUND
        1 * consulClient.watchValues("path/to/NOT_FOUND", false, null) >> Mono.error(exception)
        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO
        watchConfiguration.getKvNotFoundLogLevel() >> org.slf4j.event.Level.WARN

        when:
        watcher.start()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.WARN == event.getLevel())
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

        watcher = new ConfigurationsWatcher(List.of("path/to/http_error"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def exception = Mock(HttpClientResponseException)
        1 * exception.getStatus() >> HttpStatus.INTERNAL_SERVER_ERROR
        1 * consulClient.watchValues("path/to/http_error", false, null) >> Mono.error(exception)
        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO
        watchConfiguration.getMaxRetryAttempts() >> 0

        when:
        watcher.start()

        then:
        def logs = listAppender.list.stream()
                .filter(event -> Level.ERROR == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "Max retry attempts 0 reached, stopping watching path=path/to/http_error"
        ((ThrowableProxy) loggingEvent.getThrowableProxy()) != null
        ((ThrowableProxy) loggingEvent.getThrowableProxy()).getThrowable() == exception

        and:
        0 * propertiesChangeHandler._
        0 * watchConfiguration.getRetryDelayMs()
    }

    void "test that client timeout errors are handled"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.WARN)

        watcher = new ConfigurationsWatcher(List.of("path/to/timeout"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def exception = ReadTimeoutException.TIMEOUT_EXCEPTION
        2 * consulClient.watchValues("path/to/timeout", false, null) >> Mono.error(exception)
        blockingQueriesConfiguration.getDelayDuration() >>> [Duration.ZERO, Duration.ofSeconds(5)]

        when:
        watcher.start()

        then:
        sleep(100)
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

        watcher = new ConfigurationsWatcher(List.of("path/to/error_other"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def exception = new RuntimeException("boom")
        1 * consulClient.watchValues("path/to/error_other", false, null) >> Mono.error(exception)
        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO
        watchConfiguration.getMaxRetryAttempts() >> 0

        when:
        watcher.start()

        then:
        sleep(100)
        def logs = listAppender.list.stream()
                .filter(event -> Level.ERROR == event.getLevel())
                .toList()
        logs.size() == 1
        def loggingEvent = logs.get(0)
        loggingEvent.getFormattedMessage() == "Max retry attempts 0 reached, stopping watching path=path/to/error_other"
        ((ThrowableProxy) loggingEvent.getThrowableProxy()) != null
        ((ThrowableProxy) loggingEvent.getThrowableProxy()).getThrowable() == exception

        and:
        0 * propertiesChangeHandler._
        0 * watchConfiguration.getRetryDelayMs()
    }

    void "test that an Exception is thrown when Watcher is already started"() {
        given:
        watcher = new ConfigurationsWatcher(List.of(), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        when:
        watcher.start()
        watcher.start()

        then:
        def caughtException = thrown(IllegalStateException)
        caughtException.message == "Watcher is already started"

        and:
        0 * consulClient._
        0 * blockingQueriesConfiguration._
        0 * propertiesChangeHandler._
    }

    void "test that ERROR are logged and watcher is stopped when and exception occurs during start"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)

        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def exception = new RuntimeException("boom")
        1 * consulClient.watchValues("path/to/yaml", false, null) >> { throw exception }
        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO

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

        watcher = new ConfigurationsWatcher(List.of(), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

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
        0 * blockingQueriesConfiguration._
        0 * propertiesChangeHandler._
    }

    void "test that stopping Watcher dispose all subscriptions"() {
        given:
        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)
        def keyValue = new KeyValue(1234, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value".getBytes()))

        // fixme mock result to check the dispose call ?
        1 * consulClient.watchValues("path/to/yaml", false, null) >> Mono.delay(Duration.ofMillis(200))
                .thenReturn(List.of(keyValue))

        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO

        when:
        watcher.start()
        watcher.stop()

        then:
        0 * propertiesChangeHandler._
        // fixme check that Disposable#dispose() is called
    }

    void "test that watch is retry when an error occurs"() {
        given:
        def conditions = new AsyncConditions()

        watcher = new ConfigurationsWatcher(List.of("path/to/yaml"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def exception = Mock(HttpClientResponseException)
        2 * exception.getStatus() >> HttpStatus.INTERNAL_SERVER_ERROR
        def keyValue = new KeyValue(1234, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value".getBytes()))
        def newKeyValue = new KeyValue(4567, "path/to/yaml", base64Encoder.encodeToString("foo.bar: value_2".getBytes()))

        2 * consulClient.watchValues("path/to/yaml", false, null) >> Mono.error(exception)
        1 * consulClient.watchValues("path/to/yaml", false, null) >> Mono.just(List.of(keyValue)) // init
        2 * consulClient.watchValues("path/to/yaml", false, 1234) >>> [
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(List.of(keyValue)), // no change
                Mono.delay(Duration.ofMillis(200))
                        .thenReturn(List.of(newKeyValue)) // change
        ]

        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO
        watchConfiguration.getMaxRetryAttempts() >> 2
        watchConfiguration.getRetryDelayMs() >> 200

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

    void "test that error is handled when max retries is reached"() {
        given:
        listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        CLASS_LOGGER.addAppender(listAppender)
        CLASS_LOGGER.setLevel(Level.WARN)

        watcher = new ConfigurationsWatcher(List.of("path/to/too_much_error"), consulClient, blockingQueriesConfiguration, propertiesChangeHandler, propertySourceReader, watchConfiguration)

        def exception = Mock(HttpClientResponseException)
        4 * exception.getStatus() >> HttpStatus.INTERNAL_SERVER_ERROR
        4 * consulClient.watchValues("path/to/too_much_error", false, null) >> Mono.error(exception)
        blockingQueriesConfiguration.getDelayDuration() >> Duration.ZERO
        watchConfiguration.getMaxRetryAttempts() >> 3
        watchConfiguration.getRetryDelayMs() >> 50

        when:
        watcher.start()

        then:
        Thread.sleep(500)

        def retryLogs = listAppender.list.stream()
                .filter(event -> Level.WARN == event.getLevel())
                .toList()
        retryLogs.size() == 3

        final def delays = new ArrayList<Long>()
        for (int i = 0; i < 3; i++) {
            def iLoggingEvent = retryLogs.get(i)
            iLoggingEvent.getMessage() == "Error detected, retrying watch ({}/{}) after delay={}ms"
            def array = iLoggingEvent.getArgumentArray()
            array.size() == 3
            array[0] == i + 1
            array[1] == 3
            delays.add(array[2] as Long)
        }
        delays[0] < delays[1]
        delays[1] < delays[2]

        def errorLogs = listAppender.list.stream()
                .filter(event -> Level.ERROR == event.getLevel())
                .toList()
        errorLogs.size() == 1
        def loggingEvent = errorLogs.get(0)
        loggingEvent.getFormattedMessage() == "Max retry attempts 3 reached, stopping watching path=path/to/too_much_error"
        ((ThrowableProxy) loggingEvent.getThrowableProxy()) != null
        ((ThrowableProxy) loggingEvent.getThrowableProxy()).getThrowable() == exception

        and:
        0 * propertiesChangeHandler._
    }
}
