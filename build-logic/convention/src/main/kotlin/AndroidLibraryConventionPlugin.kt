import com.android.build.api.dsl.LibraryExtension
import com.orbin.buildlogic.configureJava
import com.orbin.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for Android library modules (data, network, media, providers, core/ui).
 * Applies the library + Kotlin plugins and shared SDK / compiler configuration.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            // Kotlin is compiled by AGP 9's built-in Kotlin support; no KGP android plugin.
            apply("com.android.library")
        }

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            compileOptions {
                configureJava()
            }
            defaultConfig {
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }
            // Library modules don't ship Android resources unless they opt in (set per module).
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                }
            }
        }
    }
}
