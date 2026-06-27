plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.compose)
}

android {
    namespace = "com.orbin.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.compose.material3)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.immutable)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
