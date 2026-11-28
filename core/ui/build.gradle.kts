plugins {
    alias(libs.plugins.orbin.kmp.compose)
}

kotlin {
    android {
        namespace = "com.orbin.core.ui"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:designsystem"))
            implementation(project(":core:model"))
            implementation(libs.kotlinx.immutable)
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
        }
    }
}
