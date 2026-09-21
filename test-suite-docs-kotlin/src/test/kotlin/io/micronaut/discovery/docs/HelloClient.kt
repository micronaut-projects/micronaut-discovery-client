package io.micronaut.discovery.docs

// tag::imports[]
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
// end::imports[]

// tag::class[]
@Client(id = "hello-world")
interface HelloClient {

    @Get("/hello")
    fun hello(): String
}
// end::class[]
