plugins {
    alias(libs.plugins.orbin.android.application)
    alias(libs.plugins.orbin.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val orbinVersionCode = providers.gradleProperty("orbin.versionCode").get().toInt()
val orbinVersionName = providers.gradleProperty("orbin.versionName").get()

android {
    namespace = "com.orbin.minimal"

    defaultConfig {
        // A distinct applicationId, so this installs alongside the full client rather than
        // replacing it. The cost is that Android sandboxes the two separately: subscriptions made
        // here are this app's own, and cannot be read out of the full client's encrypted database.
        applicationId = "com.orbin.minimal"
        // Shared with the full client via gradle.properties: the two ship as a matched pair,
        // built from the same commit, and a version skew between them would be meaningless.
        versionCode = orbinVersionCode
        versionName = orbinVersionName
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
}
