import com.orbin.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for `:feature:*` modules. Composes the library, Compose, and Hilt
 * conventions and adds the dependencies every feature needs: the core UI/designsystem,
 * domain layer, navigation, lifecycle, and Compose tooling for ViewModels.
 *
 * Keeping this in one place means a new feature module is a three-line build file.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("orbin.android.library")
            apply("orbin.android.compose")
            apply("orbin.android.hilt")
        }

        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":domain"))

            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("compose-material3").get())
            add("implementation", libs.findLibrary("kotlinx-immutable").get())

            add("testImplementation", project(":core:testing"))
            add("androidTestImplementation", project(":core:testing"))
        }
    }
}
