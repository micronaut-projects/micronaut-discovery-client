package io.micronaut.discovery.consul.watch

import io.micronaut.context.BeanContext
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.context.event.StartupEvent
import spock.lang.Shared
import spock.lang.Specification

class WatchTriggerSpec extends Specification {

    @Shared
    WatchTrigger watcherTrigger = new WatchTrigger()

    BeanContext beanContext = Mock()
    Watcher watcher = Mock()

    void "test that the Watch is started on StartupEvent"() {
        given:
        beanContext.getBean(Watcher) >> watcher

        when:
        watcherTrigger.onStart(new StartupEvent(beanContext))

        then:
        1 * watcher.start()
        0 * watcher._
    }

    void "test that the Watch is stopped on ShutdownEvent"() {
        given:
        beanContext.getBean(Watcher) >> watcher

        when:
        watcherTrigger.onShutdown(new ShutdownEvent(beanContext))

        then:
        1 * watcher.stop()
        0 * watcher._
    }
}
