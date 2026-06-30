plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.orbin.core.designsystem"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Roborazzi screenshot tests are driven by the dedicated record/verify Roborazzi tasks. Keep them
// out of the aggregate `test` task, which otherwise runs them without baselines.
tasks.withType<Test>().configureEach {
    if (name.startsWith("test")) {
        filter {
            excludeTestsMatching("*ScreenshotTest")
            // Screenshot tests are the only tests here, so these tasks end up empty.
            isFailOnNoMatchingTests = false
        }
    }
}

dependencies {
    api(libs.compose.material3)
    api(libs.compose.material3.window.size)
    api(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
