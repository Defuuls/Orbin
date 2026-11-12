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

        // The Compose resources below are packaged as Android assets, which needs this on.
        androidResources {
            enable = true
        }
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
            implementation(project(":core:ui"))
            implementation(project(":core:reader"))
            implementation(project(":storage"))
            implementation(project(":core:data"))
            implementation(project(":core:graph"))
            implementation(project(":core:network"))
            implementation(project(":core:model"))
            implementation(project(":provider:api"))
            implementation(project(":provider:vichan"))
            implementation(project(":provider:lynxchan"))
            implementation(libs.cmp.navigationevent.compose)
            implementation(libs.cmp.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.immutable)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.room.runtime)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
        }
        // Shared logic tests live in androidHostTest so `./gradlew test` (CI unit tests) runs
        // them on the JVM and they are not compiled into the Kotlin/Native test binary.
        // Tests that need UIKit, AVKit, or other native APIs stay in iosTest.
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.robolectric)
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.coil.test)
        }
    }
}

// The few strings the iOS app says itself; the shared screens bring their own.
compose.resources {
    packageOfResClass = "com.orbin.ios.resources"
}

// Record native test case names in CI so media decode smoke tests are distinguishable from builds.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
