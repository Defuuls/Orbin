plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.home"

    // Opt in: library modules don't ship Android resources by default (see gradle.properties).
    // This module's user-facing strings live in res/values/strings.xml so they can be translated.
    androidResources {
        enable = true
    }
}

dependencies {
    implementation(project(":media"))
}
