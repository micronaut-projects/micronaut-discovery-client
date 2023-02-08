import java.util.Locale

plugins {
    id("org.graalvm.buildtools.native") version "0.9.14"
    id("io.micronaut.application") version "3.7.0"
    id("io.micronaut.test-resources") version "3.7.0"
}

repositories {
    mavenCentral()
}

micronaut {
    version.set(libs.versions.micronaut.asProvider())
    testResources {
        enabled.set(true)
    }
}

dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.http)
    testImplementation(mn.micronaut.json.core)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.test.junit5)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.awaitility)

    testImplementation(projects.discoveryClient)

    testResourcesImplementation("io.micronaut.testresources:micronaut-test-resources-testcontainers:1.2.3")

    testRuntimeOnly(mn.logback)
}

val isGraalVMJdk = listOf("jvmci.Compiler", "java.vendor.version", "java.vendor").any {
    System.getProperty(it)?.toLowerCase(Locale.ENGLISH)?.contains("graal") == true
}

tasks {
    test {
        useJUnitPlatform()
    }

    named("check") {
        if (isGraalVMJdk) {
            dependsOn("nativeTest")
        }
    }
}

graalvmNative {
    toolchainDetection.set(false)
}
