plugins {
    id("io.micronaut.build.internal.discovery-client-tests-consul")
    id("org.graalvm.buildtools.native") version "1.1.4"
    id("io.micronaut.library") version "5.0.2"
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
