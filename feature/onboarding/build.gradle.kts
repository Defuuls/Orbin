plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.onboarding"
}

dependencies {
    implementation(project(":provider:api"))
}
