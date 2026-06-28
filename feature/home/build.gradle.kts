plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.home"
}

dependencies {
    implementation(project(":media"))
    implementation(project(":provider:api"))
}
