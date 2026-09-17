package io.micronaut.discovery.docs

// tag::imports[]
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import jakarta.inject.Inject
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class HelloService {

    @field:Client(id = "hello-world")
    @Inject
    lateinit var httpClient: HttpClient

    fun hello(): String = httpClient.toBlocking().retrieve("/hello")
}
// end::class[]
