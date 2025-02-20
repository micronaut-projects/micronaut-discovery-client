plugins {
    id("io.micronaut.build.internal.discovery-client-base")
    id("io.micronaut.minimal.library") version "4.4.4"
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    implementation(projects.micronautDiscoveryClient)
    implementation(mnTest.micronaut.test.junit5)
    implementation(mnTest.assertj.core)
    implementation(mnSerde.micronaut.serde.jackson)
    implementation(libs.testcontainers.junit.jupiter)
    implementation(libs.testcontainers.consul)
    implementation(libs.awaitility)
    implementation(libs.commons.lang3)

    testImplementation(libs.junit.platform.engine)

    testRuntimeOnly(mn.micronaut.http.client)
    testRuntimeOnly(mn.snakeyaml)
    testRuntimeOnly(mnLogging.logback.classic)
}

micronaut {
    version.set(libs.versions.micronaut.platform.get())
    testRuntime("junit5")
}
