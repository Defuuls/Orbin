plugins {
    alias(libs.plugins.orbin.kmp.compose)
}

kotlin {
    android {
        namespace = "com.orbin.uipulse"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:designsystem"))
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
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}
