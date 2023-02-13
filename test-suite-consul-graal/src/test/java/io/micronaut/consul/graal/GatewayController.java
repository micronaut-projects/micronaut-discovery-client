package io.micronaut.consul.graal;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/api")
public class GatewayController {

    private final HelloClient helloClient;

    public GatewayController(HelloClient helloClient) {
        this.helloClient = helloClient;
    }

    @Get("/hello/{name}")
    public String sayHi(String name) {
        return helloClient.sayHi(name);
    }
}
