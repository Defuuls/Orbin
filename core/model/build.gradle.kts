plugins {
    alias(libs.plugins.orbin.jvm.library)
}

dependencies {
    api(libs.kotlinx.immutable)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
