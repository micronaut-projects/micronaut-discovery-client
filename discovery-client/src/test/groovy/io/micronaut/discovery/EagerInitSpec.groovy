package io.micronaut.discovery

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EagerInitSpec extends Specification {

    void 'test run with eager init'() {
        given:
        def context = ApplicationContext.builder().eagerInitSingletons(true).eagerInitConfiguration(true)
                .start()

        expect: "context starts up successfully"
        context != null
    }
}
