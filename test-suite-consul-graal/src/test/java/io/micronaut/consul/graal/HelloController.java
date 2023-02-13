package io.micronaut.consul.graal;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/")
public class HelloController {

    @Get("/hello/{name}")
    public String sayHi(String name) {
        return "Hello " + name;
    }
}
