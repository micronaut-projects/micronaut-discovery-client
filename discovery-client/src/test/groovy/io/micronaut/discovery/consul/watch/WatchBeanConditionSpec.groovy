package io.micronaut.discovery.consul.watch

import io.micronaut.discovery.consul.condition.RequiresConsul
import spock.lang.Specification

class WatchBeanConditionSpec extends Specification {

    void "watch beans declare the consul condition directly"() {
        expect:
        WatchFactory.getAnnotation(RequiresConsul) != null
        WatchTrigger.getAnnotation(RequiresConsul) != null
    }
}
