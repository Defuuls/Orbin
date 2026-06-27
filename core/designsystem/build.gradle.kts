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

// Roborazzi only configures the debug unit-test variant, so screenshot tests must not run under
// `testReleaseUnitTest` (which the aggregate `test` task also invokes).
tasks.withType<Test>().configureEach {
    if (name.contains("Release")) {
        filter { excludeTestsMatching("*ScreenshotTest") }
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
