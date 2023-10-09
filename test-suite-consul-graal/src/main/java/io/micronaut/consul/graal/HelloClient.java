package io.micronaut.consul.graal;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@Requires(property = "spec.name", value = "ConsulTest")
@Client(id = "service-discovery")
public interface HelloClient {

    @Get("/hello/{name}")
    String sayHi(String name);
}
