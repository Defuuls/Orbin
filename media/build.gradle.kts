plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.compose)
    alias(libs.plugins.orbin.android.hilt)
}

android {
    namespace = "com.orbin.media"

    // Opt in: library modules don't ship Android resources by default (see gradle.properties).
    // This module's user-facing strings live in res/values/strings.xml so they can be translated.
    androidResources {
        enable = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain"))
    implementation(project(":network"))

    implementation(libs.androidx.core.ktx)
    // Named directly rather than leaned on transitively: this module builds requests itself,
    // through the shared client :network provides.
    implementation(libs.okhttp)

    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)

    api(libs.media3.exoplayer)
    api(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    // MediaThumbnail's scaling is only visible in the raster it produces, so the test for it
    // renders the composable and reads pixels back — Robolectric to host the render, coil-test to
    // serve a thumbnail of a known size without a network.
    testImplementation(libs.robolectric)
    testImplementation(libs.coil.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
