plugins {
    alias(libs.plugins.orbin.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.orbin.feature.gallery"

    // Opt in: library modules don't ship Android resources by default (see gradle.properties).
    // This module's user-facing strings live in res/values/strings.xml so they can be translated.
    androidResources {
        enable = true
    }
}

// Same arrangement as :core:designsystem — keep screenshot tests out of the aggregate `test` task,
// which would otherwise run them with no baselines, but let the Roborazzi tasks through.
val roborazziInvoked =
    gradle.startParameter.taskNames.any { it.contains("roborazzi", ignoreCase = true) }

tasks.withType<Test>().configureEach {
    if (name.startsWith("test") && !roborazziInvoked) {
        filter {
            excludeTestsMatching("*ScreenshotTest")
            isFailOnNoMatchingTests = false
        }
    }
}

dependencies {
    implementation(project(":media"))

    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
