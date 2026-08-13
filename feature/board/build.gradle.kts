plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.board"

    // Opt in: library modules don't ship Android resources by default (see gradle.properties).
    // This module's user-facing strings live in res/values/strings.xml so they can be translated.
    androidResources {
        enable = true
    }
}

dependencies {
    implementation(project(":media"))
    implementation(libs.androidx.paging.compose)

    // asSnapshot(), for asserting on what the catalog's PagingData actually holds.
    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(project(":core:designsystem"))
    debugImplementation(libs.compose.ui.test.manifest)
}
