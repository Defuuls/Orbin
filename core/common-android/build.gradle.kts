plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.hilt)
}

android {
    namespace = "com.orbin.core.common.android"
}

// The Android and Hilt side of core:common: dispatcher qualifiers and their bindings, external
// links and the app lock signal. Platform-neutral primitives stay in :core:common, which this
// re-exports so a module needs only one of the two.
dependencies {
    api(project(":core:common"))
    // The shared HTTPS link rule (LinkPolicy), so Android and iOS open exactly the same links.
    implementation(project(":domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    // WebView's cookie and storage managers only exist on a device; Robolectric stands in for them.
    testImplementation(libs.robolectric)
}
