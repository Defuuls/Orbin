plugins {
    alias(libs.plugins.orbin.kmp.compose)
    alias(libs.plugins.roborazzi)
}

kotlin {
    android {
        namespace = "com.orbin.core.designsystem"

        // Opt in: library modules don't ship Android resources by default (see gradle.properties).
        // This module carries the app-icon drawable the splash screen shows.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.cmp.material3)
            api(libs.cmp.material3.window.size)
            api(libs.cmp.material.icons.extended)
        }
        androidMain.dependencies {
            // Pins the Android side to the app's Compose release rather than the older one the
            // multiplatform artifacts were built against.
            implementation(project.dependencies.platform(libs.compose.bom))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// Roborazzi screenshot tests are driven by the dedicated record/verify Roborazzi tasks. Keep them
// out of the aggregate `test` task, which otherwise runs them without baselines.
//
// The Roborazzi tasks *are* the host test task with extra system properties, so an unconditional
// name filter excluded them from the very tasks that exist to run them. Screenshot tests are the
// only tests in this module, so record and verify quietly executed nothing and reported success —
// green, with no images written and nothing checked. Skip the filter when Roborazzi is what was
// asked for.
val roborazziInvoked =
    gradle.startParameter.taskNames.any { it.contains("roborazzi", ignoreCase = true) }

tasks.withType<Test>().configureEach {
    if (name.startsWith("test") && !roborazziInvoked) {
        filter {
            excludeTestsMatching("*ScreenshotTest")
            // Screenshot tests are the only tests here, so these tasks end up empty.
            isFailOnNoMatchingTests = false
        }
    }
}
