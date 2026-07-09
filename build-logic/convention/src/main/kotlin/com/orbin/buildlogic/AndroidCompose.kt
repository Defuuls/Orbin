package com.orbin.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Enables Jetpack Compose for an Android module and wires the Compose BOM, common Compose
 * dependencies, and Compose compiler metrics/reports for build diagnostics.
 */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }

        dependencies {
            val bom = libs.findLibrary("compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-ui-graphics").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("compose-foundation").get())
            add("implementation", libs.findLibrary("compose-animation").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }

    extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
        // Strong skipping is enabled by default in Kotlin 2.0.20+. Compiler metrics/reports are
        // useful for chasing recomposition regressions but add measurable compile-time overhead,
        // so they are opt-in: ./gradlew assembleDebug -Porbin.composeMetrics=true
        if (providers.gradleProperty("orbin.composeMetrics").map { it.toBoolean() }.getOrElse(false)) {
            metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        }
    }
}
