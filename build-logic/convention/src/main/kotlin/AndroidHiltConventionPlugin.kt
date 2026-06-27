import com.orbin.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Applies Hilt + KSP and wires the Hilt runtime and compiler. Modules that need DI apply
 * `orbin.android.hilt`; the app additionally aggregates all `@Module`s.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.google.devtools.ksp")
            apply("com.google.dagger.hilt.android")
        }

        dependencies {
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
        }
    }
}
