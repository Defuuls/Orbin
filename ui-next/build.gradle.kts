plugins {
    alias(libs.plugins.orbin.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.orbin.uinext"
}

// A proposal, not a shipped surface: nothing depends on this module yet, and the screens are
// stateless composables fed by sample state so they can be rendered and judged before any of it
// is wired to a ViewModel. Same arrangement as :core:designsystem — screenshot tests are kept out
// of the aggregate `test` task, which would otherwise run them with no baselines.
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
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
