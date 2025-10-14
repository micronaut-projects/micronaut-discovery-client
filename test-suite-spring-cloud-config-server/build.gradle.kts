plugins {
    id("java-library")
    id("io.micronaut.build.internal.discovery-client-tests")
}
dependencies {
    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mn.micronaut.http.client)
    testRuntimeOnly(mnLogging.logback.classic)
    testImplementation(projects.micronautDiscoveryClient)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
tasks.withType<Test> {
    useJUnitPlatform()
}
