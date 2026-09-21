package io.micronaut.discovery.docs;

// tag::imports[]
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class HelloService {

    @Client(id = "hello-world")
    @Inject
    HttpClient httpClient;

    public String hello() {
        return httpClient.toBlocking().retrieve("/hello");
    }
}
// end::class[]
