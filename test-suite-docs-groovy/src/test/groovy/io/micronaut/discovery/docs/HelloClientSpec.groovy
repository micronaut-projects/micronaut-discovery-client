package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "HelloClientSpec")
@MicronautTest
class HelloClientSpec extends Specification {

    @Inject
    HelloClient helloClient

    @Inject
    HelloService helloService

    void "the declarative client resolves the hello-world service"() {
        expect:
        helloClient.hello() == "Hello World"
    }

    void "the injected HttpClient resolves the hello-world service"() {
        expect:
        helloService.hello() == "Hello World"
    }
}
