plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.compose)
}

android {
    namespace = "com.orbin.core.designsystem"
}

dependencies {
    api(libs.compose.material3)
    api(libs.compose.material3.window.size)
    api(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
