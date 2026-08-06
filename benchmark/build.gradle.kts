plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.orbin.benchmark"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // The macrobenchmark tooling's own floor, independent of the app's minSdk.
        minSdk = 28
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Generation needs real hardware: a rooted emulator or an unlocked physical device. There is
    // no CI path for this, which is why the generated profile is committed rather than rebuilt.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}
