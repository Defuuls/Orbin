plugins {
    alias(libs.plugins.orbin.android.application)
    alias(libs.plugins.orbin.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

val orbinVersionCode = providers.gradleProperty("orbin.versionCode").get().toInt()
val orbinVersionName = providers.gradleProperty("orbin.versionName").get()

android {
    namespace = "com.orbin.app"

    defaultConfig {
        applicationId = "com.orbin.app"
        // Declared in gradle.properties so the two shipped APKs cannot drift apart in version.
        versionCode = orbinVersionCode
        versionName = orbinVersionName
        testInstrumentationRunner = "com.orbin.app.HiltTestRunner"
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Architecture layers
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

    // Feature modules
    implementation(project(":ui-next"))
    implementation(project(":feature:home"))
    implementation(project(":feature:board"))
    implementation(project(":feature:thread"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:history"))
    implementation(project(":feature:search"))
    implementation(project(":feature:gallery"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:onboarding"))

    // Compose + framework
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)

    // Applies the baseline profile on devices without Play's profile delivery.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.datastore.preferences)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
