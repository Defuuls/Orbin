import com.orbin.buildlogic.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for platform-neutral modules that iOS will share with Android.
 *
 * The JVM target is what Android modules consume today, so nothing downstream changes. The iOS
 * targets make the compiler reject any JVM-only API in `commonMain`, which is the point: a module
 * on this plugin is one the iOS app can depend on as-is.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
                iosArm64()
                iosSimulatorArm64()

                compilerOptions {
                    freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
                }
            }

            configureKotlin()

            // CI runs `./gradlew test`. A multiplatform module has `jvmTest` rather than `test`, so
            // without this alias its tests would silently drop out of that run.
            tasks.register("test") {
                group = "verification"
                description = "Runs the JVM tests, the same entry point every other module offers."
                dependsOn("jvmTest")
            }
        }
    }
}
