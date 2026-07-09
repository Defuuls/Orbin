import com.android.build.api.dsl.ApplicationExtension
import com.orbin.buildlogic.configureAndroidCompose
import com.orbin.buildlogic.configureJava
import com.orbin.buildlogic.configureKotlinAndroid
import com.orbin.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for the single `:app` module. Configures the application plugin, Kotlin,
 * Compose, and the target SDK / version code defaults.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.application")
            // Kotlin is compiled by AGP 9's built-in Kotlin support; applying
            // org.jetbrains.kotlin.android is no longer needed (and its BaseExtension cast
            // crashes against the AGP 9 new DSL).
            apply("orbin.android.compose")
        }

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            compileOptions {
                configureJava()
            }
            defaultConfig {
                targetSdk = libs.findVersion("targetSdk").get().requiredVersion.toInt()
                vectorDrawables.useSupportLibrary = true
            }
            // Enable predictive back system animations at the app level.
            // (android:enableOnBackInvokedCallback is set in the manifest.)
            packaging {
                resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            }
        }
    }
}
