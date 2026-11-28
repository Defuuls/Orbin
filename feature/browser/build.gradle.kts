plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.browser"
}

// The in-app browser. It runs in its own process (see InAppBrowser in core:common-android), so
// it uses no dependency injection and never opens the settings store.
dependencies {
    implementation(project(":ui-next"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
}
