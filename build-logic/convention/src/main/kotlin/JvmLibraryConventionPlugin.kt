import com.orbin.buildlogic.configureKotlin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for pure-Kotlin/JVM modules (`:domain`, `:provider:api`). These layers
 * must stay free of any Android dependency so the architecture boundary is enforced by the
 * compiler, not by convention.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.jvm")
        }

        configureKotlin()

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
