import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.LibraryExtension
import com.orbin.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Enables Jetpack Compose for a module. Works for both application and library modules by
 * detecting which Android plugin is applied. Also applies the Compose compiler Gradle plugin.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        when {
            pluginManager.hasPlugin("com.android.application") ->
                extensions.configure<ApplicationExtension> { configureAndroidCompose(this) }

            pluginManager.hasPlugin("com.android.library") ->
                extensions.configure<LibraryExtension> { configureAndroidCompose(this) }
        }
    }
}
