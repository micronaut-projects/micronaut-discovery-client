plugins {
    id("io.micronaut.build.internal.discovery-client-tests-consul")
    id("org.graalvm.buildtools.native") version "1.0.0"
    id("io.micronaut.library") version "4.6.2"
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
