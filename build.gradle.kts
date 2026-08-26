// Top-level build file. Convention plugins live in `build-logic` and are applied per-module.
//
// Security dependency pins are declared once, as data, in `gradle/security-pins.txt`, and applied
// to both scopes that need them: the plugin classpath immediately below, and every project
// configuration further down. See that file for the format, and for what went wrong when the two
// scopes each spelled the rules out longhand.

buildscript {
    // A Kotlin build script's buildscript block runs before any top-level declaration in the same
    // file exists — that asymmetry is exactly why the pin rules were duplicated in the first
    // place. So the table is read and the rule is built here, in the scope that runs first, and
    // parked on `extra` for the project side further down to pick up and reuse verbatim.
    val pins = providers.fileContents(
        layout.projectDirectory.file("gradle/security-pins.txt"),
    ).asText.get()
        .lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val (coordinate, version, reason) = line.split('|').map(String::trim)
            Triple(
                Regex(coordinate.split("*").joinToString(".*") { Regex.escape(it) }),
                version,
                reason,
            )
        }
        .toList()

    // Numeric segments, compared left to right and zero-padded: 2.0.6 < 2.0.6.1, 4.5.13 < 4.5.14.
    // Non-numeric parts (".Final", "-alpha") are ignored — no pin here distinguishes on them.
    fun segments(value: String): List<Long> =
        Regex("""\d+""").findAll(value).map { it.value.toLongOrNull() ?: 0L }.toList()

    fun outranks(pinned: String, requested: String): Boolean {
        val left = segments(pinned)
        val right = segments(requested)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0L }
            val b = right.getOrElse(index) { 0L }
            if (a != b) return a > b
        }
        return false
    }

    // A pin is a floor, not an exact version. Forcing exactly downgrades anything that already
    // resolved higher, which is how the httpclient pin was quietly pulling 4.5.14 back to 4.5.13 —
    // an older build than the one the tree had asked for, in the name of security.
    val applyPins: (org.gradle.api.artifacts.DependencyResolveDetails) -> Unit = { details ->
        val coordinate = "${details.requested.group}:${details.requested.name}"
        val pin = pins.firstOrNull { it.first.matches(coordinate) }
        if (pin != null && outranks(pin.second, details.requested.version.orEmpty())) {
            details.useVersion(pin.second)
            details.because(pin.third)
        }
    }
    extra["orbin.securityPinRule"] = applyPins

    configurations.classpath {
        resolutionStrategy.eachDependency { applyPins(this) }
    }
}

// Plugins are declared here with `apply false` so their classpath is available to subprojects.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
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

// The very same rule the plugin classpath was patched with, reused rather than restated.
@Suppress("UNCHECKED_CAST")
val applySecurityPin = extra["orbin.securityPinRule"] as (DependencyResolveDetails) -> Unit

fun ResolutionStrategy.applySecurityDependencyPatches() {
    eachDependency { applySecurityPin(this) }
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

    // Gradle 9.4+ fails test tasks that discover zero tests. Several feature modules have no
    // unit tests yet, but their unit-test classpaths still contain generated classes, which
    // trips the "test sources present" heuristic. Restore the pre-9.4 behavior.
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
    }
}
