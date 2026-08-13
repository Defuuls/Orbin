plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.downloads"

    // Opt in: library modules don't ship Android resources by default (see gradle.properties).
    // This module's user-facing strings live in res/values/strings.xml so they can be translated.
    androidResources {
        enable = true
    }
}

dependencies {
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:designsystem"))
    debugImplementation(libs.compose.ui.test.manifest)
}
