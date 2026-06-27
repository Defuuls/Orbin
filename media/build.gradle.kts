plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.compose)
    alias(libs.plugins.orbin.android.hilt)
}

android {
    namespace = "com.orbin.media"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":network"))

    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
