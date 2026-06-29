// Top-level build file. Convention plugins live in `build-logic` and are applied per-module.
// Plugins are declared here with `apply false` so their classpath is available to subprojects.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
}

fun ResolutionStrategy.applySecurityDependencyPatches() {
    eachDependency {
        when {
            requested.group == "io.netty" && requested.name.startsWith("netty-") -> {
                useVersion("4.1.135.Final")
                because("Dependabot reports multiple Netty CVEs in the Android Gradle Plugin transitive classpath.")
            }

            requested.group == "org.bouncycastle" && requested.name in setOf(
                "bcprov-jdk18on",
                "bcpkix-jdk18on",
                "bcutil-jdk18on",
            ) -> {
                useVersion("1.84")
                because("Dependabot reports Bouncy Castle CVEs in the Android Gradle Plugin transitive classpath.")
            }

            requested.group == "org.apache.commons" && requested.name == "commons-compress" -> {
                useVersion("1.28.0")
                because("Dependabot reports Apache Commons Compress CVEs in the Android Gradle Plugin transitive classpath.")
            }

            requested.group == "org.jdom" && requested.name == "jdom2" -> {
                useVersion("2.0.6.1")
                because("Dependabot reports a JDOM XXE vulnerability in the Android Gradle Plugin transitive classpath.")
            }
        }
    }
}

configurations.configureEach {
    resolutionStrategy.applySecurityDependencyPatches()
}

// Apply code-quality tooling uniformly. Centralizing it here keeps per-module build files thin
// and guarantees the `detekt` / `ktlintCheck` tasks exist on every module for CI.
subprojects {
    configurations.configureEach {
        resolutionStrategy.applySecurityDependencyPatches()
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.4.1")
        android.set(true)
        ignoreFailures.set(false)
        filter {
            // Exclude generated sources.
            exclude { it.file.path.contains("/build/") }
        }
    }

    // detekt should analyze with type resolution where possible and not fail the whole run early.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
        }
    }
}
