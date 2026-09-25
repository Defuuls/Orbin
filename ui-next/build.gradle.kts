plugins {
    alias(libs.plugins.orbin.kmp.compose)
    alias(libs.plugins.roborazzi)
}

kotlin {
    android {
        namespace = "com.orbin.uinext"

        // Opt in: library modules don't ship Android resources by default (see gradle.properties).
        // The Compose resources below are packaged as Android assets, which needs this on.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:designsystem"))
            implementation(libs.cmp.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // Pins the Android side to the app's Compose release rather than the older one the
            // multiplatform artifacts were built against.
            implementation(project.dependencies.platform(libs.compose.bom))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// This module draws every screen in the app, so every word a reader sees is declared here, in
// `commonMain/composeResources` so that Android and iOS read the same strings.
compose.resources {
    packageOfResClass = "com.orbin.uinext.resources"
}

// The screens are stateless composables fed by sample state, so each one can be rendered and
// judged without a ViewModel. Same arrangement as :core:designsystem — screenshot tests are kept
// out of the aggregate `test` task, which would otherwise run them with no baselines. Tests that
// are not screenshots (the window-insets checks) still run there, which is the point of the filter
// matching on name rather than excluding the whole source set.
val roborazziInvoked =
    gradle.startParameter.taskNames.any { it.contains("roborazzi", ignoreCase = true) }

tasks.withType<Test>().configureEach {
    if (name.startsWith("test") && !roborazziInvoked) {
        filter {
            excludeTestsMatching("*ScreenshotTest")
            isFailOnNoMatchingTests = false
        }
    }
}
