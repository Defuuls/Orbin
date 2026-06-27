plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.gallery"
}

dependencies {
    implementation(project(":media"))
}
