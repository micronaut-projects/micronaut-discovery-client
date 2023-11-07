plugins {
    id("io.micronaut.build.internal.discovery-client-tests-consul")
    id("org.graalvm.buildtools.native") version "0.9.28"
    id("io.micronaut.library") version "4.1.2"
    id("io.micronaut.test-resources") version "4.1.1"
}

dependencies {
    testImplementation(mnSerde.micronaut.serde.jackson)
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
