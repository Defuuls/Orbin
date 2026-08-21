import java.util.Properties

plugins {
    alias(libs.plugins.orbin.android.application)
    alias(libs.plugins.orbin.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use(::load)
        }
    }

fun signingValue(name: String): String? =
    System.getenv("ORBIN_$name")
        ?: keystoreProperties.getProperty(name.lowercase().replace("_", "."))

val releaseStoreFile = signingValue("KEYSTORE_FILE")
val hasReleaseSigning =
    !releaseStoreFile.isNullOrBlank() &&
        !signingValue("KEYSTORE_PASSWORD").isNullOrBlank() &&
        !signingValue("KEY_ALIAS").isNullOrBlank() &&
        !signingValue("KEY_PASSWORD").isNullOrBlank()

// Only guard tasks that produce a shippable artifact. Matching every task name containing
// "Release" also caught things like `generateReleaseBaselineProfile`, which needs a
// release-*shaped* build but never leaves the machine — failing it for missing release secrets
// is a confusing answer to a question the developer did not ask.
val buildsShippableRelease =
    gradle.startParameter.taskNames.any { task ->
        val name = task.substringAfterLast(':')
        (name.startsWith("assemble") || name.startsWith("bundle")) &&
            name.contains("Release", ignoreCase = true) &&
            !name.contains("NonMinified", ignoreCase = true) &&
            !name.contains("Benchmark", ignoreCase = true)
    }

if (buildsShippableRelease) {
    check(hasReleaseSigning) {
        "Release signing is not configured. Set ORBIN_KEYSTORE_FILE, " +
            "ORBIN_KEYSTORE_PASSWORD, ORBIN_KEY_ALIAS, and ORBIN_KEY_PASSWORD, " +
            "or create an ignored keystore.properties file."
    }
}

android {
    namespace = "com.orbin.app"

    defaultConfig {
        applicationId = "com.orbin.app"
        versionCode = 112
        versionName = "94-Fusilli"
        testInstrumentationRunner = "com.orbin.app.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            } else {
                // The developer guide has long promised that release builds fall back to debug
                // signing when secrets are absent; nothing implemented it, so the config was
                // simply empty and AGP failed later with a less helpful message. Borrowing the
                // debug key is what makes baseline profile generation work on a machine that has
                // no release secrets — which is every machine except CI.
                initWith(getByName("debug"))
            }
        }
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
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.datastore.preferences)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
