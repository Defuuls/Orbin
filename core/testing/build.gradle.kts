plugins {
    alias(libs.plugins.orbin.android.library)
}

android {
    namespace = "com.orbin.core.testing"
}

dependencies {
    api(project(":core:model"))
    api(libs.junit)
    api(libs.truth)
    api(libs.turbine)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
    api(libs.kotlinx.immutable)
}
