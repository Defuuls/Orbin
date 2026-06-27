import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import com.orbin.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Applies the Room Gradle plugin + KSP, exports schemas for migration testing, and wires
 * the Room runtime/ktx/paging dependencies.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("androidx.room")
            apply("com.google.devtools.ksp")
        }

        extensions.configure<RoomExtension> {
            // Schemas are versioned in-repo so migrations can be tested deterministically.
            schemaDirectory("$projectDir/schemas")
        }

        extensions.configure<KspExtension> {
            arg("room.generateKotlin", "true")
        }

        dependencies {
            add("implementation", libs.findLibrary("room-runtime").get())
            add("implementation", libs.findLibrary("room-ktx").get())
            add("implementation", libs.findLibrary("room-paging").get())
            add("ksp", libs.findLibrary("room-compiler").get())
        }
    }
}
