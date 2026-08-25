plugins {
    alias(libs.plugins.orbin.android.application)
    alias(libs.plugins.orbin.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

val minimalVersionCode = providers.gradleProperty("orbin.minimalVersionCode").get().toInt()
val minimalVersionName = providers.gradleProperty("orbin.minimalVersionName").get()

android {
    namespace = "com.orbin.minimal"

    defaultConfig {
        // A distinct applicationId, so this installs alongside the full client rather than
        // replacing it. The cost is that Android sandboxes the two separately: subscriptions made
        // here are this app's own, and cannot be read out of the full client's encrypted database.
        applicationId = "com.orbin.minimal"
        // Its own version line, declared in gradle.properties. This app releases on its own
        // cadence under `minimal-v*` tags, so it does not inherit the full client's number.
        versionCode = minimalVersionCode
        versionName = minimalVersionName
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // The screenshot tests compose real screens, which resolve strings and themes.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Same arrangement as :core:designsystem — keep screenshot tests out of the aggregate `test` task,
// which would otherwise run them with no baselines, but let the Roborazzi tasks through.
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

dependencies {
    // The same architecture layers the full client uses. Sharing them is the point: this is a
    // different front end over identical providers, caching, filtering and encrypted storage —
    // not a second implementation that could drift from the first.
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":network"))
    implementation(project(":media"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))

    // Providers (registered via Hilt @IntoSet)
    implementation(project(":provider:vichan"))
    implementation(project(":provider:lynxchan"))

    // Only two feature modules. :feature:home supplies the subscribed-feed aggregation, and
    // :feature:thread the reader — a subscription list you cannot read from is not a reader.
    // Everything else the full client offers is deliberately absent.
    implementation(project(":feature:home"))
    implementation(project(":feature:thread"))
    // Only for the full-screen image viewer a thread's inline media opens into.
    implementation(project(":feature:gallery"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
