plugins {
    alias(libs.plugins.orbin.kmp.compose)
    alias(libs.plugins.roborazzi)
}

// "What's new" in both apps is this release's CHANGELOG.md section, compiled in at build time, so
// cutting a release (which dates that section) is all it takes for Android and iOS to show it.
val generateReleaseNotes by tasks.registering(GenerateReleaseNotes::class) {
    changelog.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
    versionName.set(providers.gradleProperty("orbin.versionName"))
    outputDir.set(layout.buildDirectory.dir("generated/releaseNotes/commonMain/kotlin"))
}

kotlin {
    android {
        namespace = "com.orbin.uinext"

        // Opt in: library modules don't ship Android resources by default (see gradle.properties).
        // The Compose resources below are packaged as Android assets, which needs this on.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateReleaseNotes.map { it.outputDir })
        }
        commonMain.dependencies {
            api(project(":core:designsystem"))
            implementation(libs.cmp.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // Pins the Android side to the app's Compose release rather than the older one the
            // multiplatform artifacts were built against.
            implementation(project.dependencies.platform(libs.compose.bom))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// This module draws every screen in the app, so every word a reader sees is declared here, in
// `commonMain/composeResources` so that Android and iOS read the same strings.
compose.resources {
    packageOfResClass = "com.orbin.uinext.resources"
}

// The screens are stateless composables fed by sample state, so each one can be rendered and
// judged without a ViewModel. Same arrangement as :core:designsystem — screenshot tests are kept
// out of the aggregate `test` task, which would otherwise run them with no baselines. Tests that
// are not screenshots (the window-insets checks) still run there, which is the point of the filter
// matching on name rather than excluding the whole source set.
val roborazziInvoked =
    gradle.startParameter.taskNames.any { it.contains("roborazzi", ignoreCase = true) }

tasks.withType<Test>().configureEach {
    if (name.startsWith("test") && !roborazziInvoked) {
        filter {
            excludeTestsMatching("*ScreenshotTest")
            isFailOnNoMatchingTests = false
        }
    }
}

/** Writes `CurrentReleaseNotes`: the `## [versionName]` section of CHANGELOG.md, as Kotlin. */
@CacheableTask
abstract class GenerateReleaseNotes : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelog: RegularFileProperty

    @get:Input
    abstract val versionName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val version = versionName.get()
        val sections = linkedMapOf<String, MutableList<String>>()
        var inRelease = false
        var heading: String? = null
        for (line in changelog.get().asFile.readLines()) {
            val release = Regex("""^## \[([^\]]+)\]""").find(line)
            if (release != null) {
                if (inRelease) break
                inRelease = release.groupValues[1] == version
                continue
            }
            if (!inRelease) continue
            when {
                line.startsWith("### ") -> heading = line.removePrefix("### ").trim()
                line.startsWith("- ") && heading != null ->
                    sections.getOrPut(heading) { mutableListOf() } += plainText(line.removePrefix("- "))
                line.startsWith("  ") && heading != null && sections[heading]?.isNotEmpty() == true -> {
                    val entries = sections.getValue(heading)
                    entries[entries.lastIndex] = entries.last() + " " + plainText(line.trim())
                }
            }
        }
        val body =
            sections.entries.joinToString(",\n") { (name, entries) ->
                "            ReleaseNoteSection(\n                ${quote(name)},\n                listOf(\n" +
                    entries.joinToString("") { "                    ${quote(it)},\n" } +
                    "                ),\n            )"
            }
        val file = outputDir.get().asFile.resolve("com/orbin/uinext/CurrentReleaseNotes.kt")
        file.parentFile.mkdirs()
        file.writeText(
            "// Generated from CHANGELOG.md by :ui-next:generateReleaseNotes. Do not edit.\n" +
                "package com.orbin.uinext\n\n" +
                "/** This build's release notes; empty when CHANGELOG.md has no section for it. */\n" +
                "val CurrentReleaseNotes: ReleaseNotes =\n" +
                "    ReleaseNotes(\n        ${quote(version)},\n        listOf(\n" +
                (if (body.isEmpty()) "" else "$body,\n") +
                "        ),\n    )\n",
        )
    }

    private fun plainText(markdown: String): String =
        markdown
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
            .replace("**", "")
            .replace("`", "")
            .trim()

    private fun quote(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
}
