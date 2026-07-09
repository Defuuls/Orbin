package com.orbin.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Shortcut for accessing the `libs` version catalog from convention plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Applies shared Android + Kotlin configuration: SDK levels, Java/Kotlin 17 targets,
 * desugaring-free defaults, and common compiler flags. Used by every Android module.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

        defaultConfig.apply {
            minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    configureKotlin()
}

/** Configures Kotlin compiler options shared across Android and pure-JVM modules. */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // Treat all warnings as errors in CI-like builds, but allow opt-in locally.
            allWarningsAsErrors.set(
                providers.gradleProperty("orbin.warningsAsErrors").map { it.toBoolean() }.orElse(false),
            )
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
}
