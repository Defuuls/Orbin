import java.util.Properties

plugins {
    alias(libs.plugins.orbin.android.application)
    alias(libs.plugins.orbin.android.hilt)
    alias(libs.plugins.kotlin.serialization)
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

if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
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
        versionCode = 44
        versionName = "25.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
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
    implementation(project(":feature:bookmarks"))
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
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
