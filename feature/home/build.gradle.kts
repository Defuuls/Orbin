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
    implementation(project(":ui-next"))

    // The feed's counts are plural resources now, so checking them means resolving them — which
    // needs a real resource table and a composition to read it from.
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
