package io.micronaut.consul.graal;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@Client(id = "service-discovery")
public interface HelloClient {

    @Get("/hello/{name}")
    String sayHi(String name);
}
