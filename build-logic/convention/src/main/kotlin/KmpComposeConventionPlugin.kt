import com.orbin.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for Compose UI that the iOS app draws with the same code as Android:
 * [KmpAndroidLibraryConventionPlugin] plus Compose Multiplatform. The host tests keep rendering
 * through Robolectric, so the screenshot goldens stay what they were.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("orbin.kmp.android")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("cmp-runtime").get())
                    implementation(libs.findLibrary("cmp-foundation").get())
                    implementation(libs.findLibrary("cmp-animation").get())
                    implementation(libs.findLibrary("cmp-ui").get())
                }
            }
        }
    }
}
