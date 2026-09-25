plugins {
    alias(libs.plugins.orbin.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Shared with iOS. Talks HTTP through Ktor; the engine comes from the host app (OkHttp on
// Android via :network, Darwin on iOS), and the Android Hilt bindings live in :app.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":provider:api"))
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.immutable)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
