plugins {
    id("io.micronaut.build.internal.discovery-client-tests-consul")
    id("org.graalvm.buildtools.native") version "0.10.1"
    id("io.micronaut.library") version "4.3.4"
    id("io.micronaut.test-resources") version "4.3.4"
}

dependencies {
    testImplementation(mn.micronaut.jackson.databind)
}

micronaut {
    version.set(libs.versions.micronaut.platform.get())
}
graalvmNative {
    toolchainDetection.set(false)
    metadataRepository {
        enabled.set(true)
    }
}
