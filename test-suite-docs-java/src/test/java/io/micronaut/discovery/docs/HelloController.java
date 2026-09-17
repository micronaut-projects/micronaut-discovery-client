package io.micronaut.discovery.docs;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

/**
 * The {@code hello-world} service the examples discover.
 */
@Requires(property = "spec.name", value = "HelloClientTest")
@Controller("/hello")
class HelloController {

    @Get
    String index() {
        return "Hello World";
    }
}
