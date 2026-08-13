package com.orbin.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/**
 * Shared Android Lint configuration.
 *
 * ktlint and detekt read Kotlin as a language; neither knows what an Android API level, a manifest
 * or a resource is. Lint is the only tool in the build that checks those, and it had never been
 * configured or run here — so `NewApi`, the manifest and permission checks, and `ObsoleteSdkInt`
 * were all unenforced.
 *
 * Each module carries its own `lint-baseline.xml` so today's findings are recorded rather than
 * fixed in one sweep; anything new fails the build. Delete a baseline entry (or the file) once its
 * findings are cleaned up — a baseline that is never shrunk is just a suppression list.
 *
 * `NewApi` is the reason this landed before the `minSdk` drop rather than after: it is the check
 * that proves no call site reaches past the floor once the floor moves.
 */
internal fun Project.configureLint(commonExtension: CommonExtension) {
    // AGP 9 exposes `lint` as a property rather than a block-taking function.
    commonExtension.lint.apply {
        baseline = file("lint-baseline.xml")

        // Fail on errors; warnings stay visible in the report without gating the build.
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false

        // Dead compatibility branches: code guarded for API levels below minSdk can never run.
        // This is a warning by default, and the kind of thing that quietly accumulates.
        //
        // One baselined instance is not actually fixable: lint wants `mipmap-anydpi-v26` merged
        // into `mipmap-anydpi` now that minSdk is past 26, but those files are `<adaptive-icon>`
        // XML, and without the v26 qualifier AAPT2 fails to link them at all — "resource
        // mipmap/ic_launcher not found". The qualifier is load-bearing for resource versioning,
        // not just an SDK gate.
        this.error.add("ObsoleteSdkInt")

        this.disable.addAll(
            listOf(
                // Lint's own bookkeeping about baseline entries, noisy while baselines are in play.
                "LintBaseline",
                "LintBaselineFixed",
                // Dependency freshness is Dependabot's job. These checks reach the network and
                // their result changes with the calendar rather than with the diff, which is not
                // something CI should fail on.
                "NewerVersionAvailable",
                "GradleDependency",
                "AndroidGradlePluginVersion",
            ),
        )

        htmlReport = true
        sarifReport = true
        textReport = false
    }
}
