package io.micronaut.consul.graal;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Requires(property = "spec.name", value = "ConsulTest")
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
