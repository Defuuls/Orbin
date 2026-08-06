plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.downloads"
}

dependencies {
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:designsystem"))
    debugImplementation(libs.compose.ui.test.manifest)
}
