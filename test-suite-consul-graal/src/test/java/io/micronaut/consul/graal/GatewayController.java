package io.micronaut.consul.graal;

import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.stream.Collectors;

@Controller("/api")
public class GatewayController {

    private final HelloClient helloClient;

    public GatewayController(HelloClient helloClient, ConsulConfiguration consulConfiguration) {
        this.helloClient = helloClient;
        System.out.println("ConsulConfiguration: " + consulConfiguration.getDefaultZone().stream().map(z -> z.getHost() + ":" + z.getPort()).collect(Collectors.toList()));
    }

    @Get("/hello/{name}")
    public String sayHi(String name) {
        return helloClient.sayHi(name);
    }
}
