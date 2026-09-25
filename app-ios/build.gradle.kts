plugins {
    alias(libs.plugins.orbin.kmp.compose)
}

// The iOS app's Kotlin half: it wires the shared providers and screens together and is linked into
// the Xcode project in `iosApp/` as the static `OrbinKit` framework. It is to iOS what `:app` is to
// Android — a composition root that nothing else depends on.
kotlin {
    android {
        // Never packaged into the Android app; the Android target exists so the shared logic here
        // is unit-tested on every `./gradlew test` run, without needing a Mac.
        namespace = "com.orbin.ios"
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "OrbinKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":ui-next"))
            implementation(project(":core:model"))
            implementation(project(":provider:api"))
            implementation(project(":provider:vichan"))
            implementation(project(":provider:lynxchan"))
            implementation(libs.cmp.navigationevent.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.immutable)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
