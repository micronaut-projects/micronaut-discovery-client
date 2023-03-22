import java.util.*
plugins {
    id("org.graalvm.buildtools.native") version "0.9.20"
    id("io.micronaut.library") version "4.0.0-SNAPSHOT"
    id("io.micronaut.test-resources") version "4.0.0-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
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
    metadataRepository {
        enabled.set(true)
    }
}
