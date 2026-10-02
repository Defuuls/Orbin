plugins {
    alias(libs.plugins.orbin.kmp.library)
}

// The request policy both apps send traffic under, shared with iOS: HTTPS-only, the header and cache
// rules, and clearing the POWBlock gate. Pure Kotlin plus Ktor. iOS applies it through the Ktor
// plugins here; Android applies the same policy through OkHttp interceptors in :network, so that
// image, video and download traffic (which goes straight to OkHttp) is covered as well.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlincrypto.sha2)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
