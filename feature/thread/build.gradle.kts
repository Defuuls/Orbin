plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.thread"
}

dependencies {
    implementation(project(":media"))

    testImplementation(project(":core:testing"))
}
