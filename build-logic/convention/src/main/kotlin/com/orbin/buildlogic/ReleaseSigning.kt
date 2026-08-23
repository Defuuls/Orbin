package com.orbin.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import java.util.Properties

/**
 * Release signing shared by every module that ships an APK.
 *
 * Lived inline in `:app` while there was only one application module. A second shippable APK made
 * the choice explicit: duplicate forty lines of credential handling and the fallback rules that go
 * with them, or state them once. Two copies of this would drift, and the way they would drift is
 * one APK silently shipping debug-signed.
 */
fun Project.configureReleaseSigning(extension: ApplicationExtension) {
    val credentials = ReleaseSigningCredentials(this)

    if (credentials.buildsShippableRelease(gradle.startParameter.taskNames)) {
        check(credentials.isComplete) {
            "Release signing is not configured. Set ORBIN_KEYSTORE_FILE, " +
                "ORBIN_KEYSTORE_PASSWORD, ORBIN_KEY_ALIAS, and ORBIN_KEY_PASSWORD, " +
                "or create an ignored keystore.properties file."
        }
    }

    with(extension) {
        signingConfigs {
            create("release") {
                if (credentials.isComplete) {
                    storeFile = rootProject.file(credentials.storeFile!!)
                    storePassword = credentials.value("KEYSTORE_PASSWORD")
                    keyAlias = credentials.value("KEY_ALIAS")
                    keyPassword = credentials.value("KEY_PASSWORD")
                } else {
                    // The developer guide has long promised that release builds fall back to debug
                    // signing when secrets are absent; nothing implemented it, so the config was
                    // simply empty and AGP failed later with a less helpful message. Borrowing the
                    // debug key is what makes baseline profile generation work on a machine that
                    // has no release secrets — which is every machine except CI.
                    initWith(getByName("debug"))
                }
            }
        }
    }
}

/** Reads signing credentials from the environment, falling back to an ignored properties file. */
private class ReleaseSigningCredentials(
    project: Project,
) {
    private val properties =
        Properties().apply {
            val file = project.rootProject.file("keystore.properties")
            if (file.exists()) file.inputStream().use(::load)
        }

    fun value(name: String): String? =
        System.getenv("ORBIN_$name")
            ?: properties.getProperty(name.lowercase().replace("_", "."))

    val storeFile: String? = value("KEYSTORE_FILE")

    val isComplete: Boolean =
        !storeFile.isNullOrBlank() &&
            !value("KEYSTORE_PASSWORD").isNullOrBlank() &&
            !value("KEY_ALIAS").isNullOrBlank() &&
            !value("KEY_PASSWORD").isNullOrBlank()

    /**
     * Whether this invocation produces something that actually leaves the machine.
     *
     * Only guards tasks that produce a shippable artifact. Matching every task name containing
     * "Release" also caught things like `generateReleaseBaselineProfile`, which needs a
     * release-*shaped* build but never leaves the machine — failing it for missing release secrets
     * is a confusing answer to a question the developer did not ask.
     */
    fun buildsShippableRelease(taskNames: List<String>): Boolean =
        taskNames.any { task ->
            val name = task.substringAfterLast(':')
            (name.startsWith("assemble") || name.startsWith("bundle")) &&
                name.contains("Release", ignoreCase = true) &&
                !name.contains("NonMinified", ignoreCase = true) &&
                !name.contains("Benchmark", ignoreCase = true)
        }
}
