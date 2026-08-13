plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.compose)
}

android {
    namespace = "com.orbin.core.ui"

    // Opt in: library modules don't ship Android resources by default (see gradle.properties).
    // This module's user-facing strings live in res/values/strings.xml so they can be translated.
    androidResources {
        enable = true
    }
}

dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.immutable)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
