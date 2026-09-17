package io.micronaut.discovery.docs

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

/**
 * The `hello-world` service the examples discover.
 */
@Requires(property = "spec.name", value = "HelloClientTest")
@Controller("/hello")
class HelloController {

    @Get
    fun index(): String = "Hello World"
}
