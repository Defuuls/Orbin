plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.settings"
}

dependencies {
    implementation(project(":ui-next"))
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:designsystem"))
    debugImplementation(libs.compose.ui.test.manifest)
}
