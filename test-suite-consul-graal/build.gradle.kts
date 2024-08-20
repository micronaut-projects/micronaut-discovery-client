plugins {
    id("java-library")
    id("io.micronaut.build.internal.discovery-client-tests")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    implementation(projects.micronautDiscoveryClient)
    implementation(mn.micronaut.http)
    implementation(mn.micronaut.http.client.core)
    implementation(mnTest.micronaut.test.junit5)
    implementation(libs.awaitility)
    implementation(platform(mnTestResources.boms.testcontainers))
    implementation(libs.testcontainers.junit.jupiter)
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
