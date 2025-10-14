package io.micronaut.discovery.consul.watch

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.discovery.consul.ConsulConfiguration
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration
import spock.lang.Specification

import java.time.Duration

import static io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration.DEFAULT_MAX_WAIT_DURATION_MINUTES

class WatchConfigurationSpec extends Specification {

    ConsulConfiguration consulConfiguration = Mock()
    ConversionService conversionService = Mock()

    BlockingQueriesConfiguration watchConfiguration = new BlockingQueriesConfiguration(consulConfiguration, conversionService)

    void "test that the default read timeout is used"() {
        given:
        1 * conversionService.convertRequired(DEFAULT_MAX_WAIT_DURATION_MINUTES, Duration.class) >> Duration.ofMinutes(10)

        when:
        def readTimeout1 = watchConfiguration.getReadTimeout()
        def readTimeout2 = watchConfiguration.getReadTimeout()

        then:
        readTimeout1.isPresent()
        readTimeout1.get().toSeconds() == 637
        readTimeout1 == readTimeout2
    }

    void "test that the read timeout is calculated when setting max wait duration"() {
        given:
        1 * conversionService.convertRequired("16s", Duration.class) >> Duration.ofSeconds(16)

        when:
        watchConfiguration.setMaxWaitDuration("16s");

        then:
        Optional<Duration> readTimeoutValue = ReflectionUtils.getFieldValue(BlockingQueriesConfiguration.class, "readTimeout", watchConfiguration)
        readTimeoutValue.isPresent()
        readTimeoutValue.get().toSeconds() == 17
    }
}
