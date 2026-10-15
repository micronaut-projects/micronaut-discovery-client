plugins {
    id("java-library")
    id("io.micronaut.build.internal.discovery-client-tests")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    implementation(projects.micronautDiscoveryClient)
    implementation(mn.micronaut.http)
    implementation(mn.micronaut.http.client)
    implementation(mn.micronaut.http.server.netty)
    implementation(mnTest.micronaut.test.junit5)
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
