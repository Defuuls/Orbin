plugins {
    alias(libs.plugins.orbin.kmp.compose)
}

kotlin {
    android {
        namespace = "com.orbin.core.ui"

        // Opt in: library modules don't ship Android resources by default (see gradle.properties).
        // The Compose resources below are packaged as Android assets, which needs this on.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:designsystem"))
            implementation(project(":core:model"))
            implementation(libs.cmp.resources)
            implementation(libs.kotlinx.immutable)
        }
        androidMain.dependencies {
            // Pins the Android side to the app's Compose release rather than the older one the
            // multiplatform artifacts were built against.
            implementation(project.dependencies.platform(libs.compose.bom))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
        }
    }
}

// The module's user-facing strings, in `commonMain/composeResources` so both platforms read them.
compose.resources {
    packageOfResClass = "com.orbin.core.ui.resources"
}
