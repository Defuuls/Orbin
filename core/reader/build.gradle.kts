plugins {
    alias(libs.plugins.orbin.kmp.compose)
    alias(libs.plugins.roborazzi)
}

kotlin {
    android {
        namespace = "com.orbin.core.reader"
        androidResources {
            enable = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:ui"))
            api(project(":ui-next"))
            implementation(project(":domain"))
            implementation(project(":provider:api"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.cmp.resources)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.robolectric)
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
        }
    }
}

compose.resources {
    packageOfResClass = "com.orbin.core.reader.resources"
}

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
