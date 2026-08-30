plugins {
    alias(libs.plugins.orbin.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.orbin.uinext"

    // Opt in: library modules don't ship Android resources by default (see gradle.properties).
    // This module draws every screen in the app, so every word a reader sees is declared here —
    // it shipped with them written inline, which meant the whole interface was English-only.
    androidResources {
        enable = true
    }
}

// The screens are stateless composables fed by sample state, so each one can be rendered and
// judged without a ViewModel. Same arrangement as :core:designsystem — screenshot tests are kept
// out of the aggregate `test` task, which would otherwise run them with no baselines. Tests that
// are not screenshots (the window-insets checks) still run there, which is the point of the filter
// matching on name rather than excluding the whole source set.
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
