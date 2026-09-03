plugins {
    alias(libs.plugins.orbin.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.immutable)
    testImplementation(libs.kotlinx.coroutines.test)
}
