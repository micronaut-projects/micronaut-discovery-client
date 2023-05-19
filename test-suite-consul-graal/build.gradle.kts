import java.util.*
plugins {
    id("org.graalvm.buildtools.native") version "0.9.22"
    id("io.micronaut.library") version "4.0.0-SNAPSHOT"
    id("io.micronaut.test-resources") version "4.0.0-SNAPSHOT"
}

repositories {
    mavenCentral()
}

micronaut {
    version.set("4.0.0-SNAPSHOT")
    //version.set(libs.versions.micronaut.asProvider())
}

dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.http)
    testImplementation(mn.micronaut.json.core)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.jackson.databind)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.awaitility)
    testImplementation(mn.snakeyaml)
    testImplementation(projects.micronautDiscoveryClient)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

graalvmNative {
    toolchainDetection.set(false)
    metadataRepository {
        enabled.set(true)
    }
}
