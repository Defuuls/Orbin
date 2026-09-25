plugins {
    alias(libs.plugins.orbin.kmp.library)
}

// Platform-neutral primitives shared with iOS. Android and Hilt pieces live in
// :core:common-android.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
