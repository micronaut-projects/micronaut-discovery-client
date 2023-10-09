package io.micronaut.consul.graal;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Requires(property = "spec.name", value = "ConsulTest")
@Controller
public class HelloController {

    @Get("/hello/{name}")
    public String sayHi(String name) {
        return "Hello " + name;
    }
}
