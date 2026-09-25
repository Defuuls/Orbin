import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.orbin.buildlogic.configureAndroidLint
import com.orbin.buildlogic.configureKotlin
import com.orbin.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for code that iOS shares with Android and that Android has to consume as an
 * Android library rather than plain JVM: Compose UI, and Room, whose Android artifact is not the
 * JVM one.
 *
 * Unlike [KmpLibraryConventionPlugin], the shared target here is Android, so host tests run through
 * Robolectric where they need to. The iOS targets make the compiler reject any Android-only API in
 * `commonMain`; what genuinely differs per platform goes behind `expect`/`actual` in `androidMain`
 * and `iosMain`.
 *
 * Each module sets its own `namespace` inside `kotlin { android { } }`.
 */
class KmpAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
                // The multiplatform Android plugin has no lint of its own; this adds the `lint` task.
                apply("com.android.lint")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                    compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()
                    minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
                    withHostTest {
                        isIncludeAndroidResources = true
                        isReturnDefaultValues = true
                    }
                    configureAndroidLint(lint)
                }
                iosArm64()
                iosSimulatorArm64()

                compilerOptions {
                    freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
                }
            }

            // Sets JVM 17 on the Android compilations along with the shared compiler flags.
            configureKotlin()

            // CI runs `./gradlew test`. The Android host tests are `testAndroidHostTest` here, so
            // without this alias they would silently drop out of that run.
            tasks.register("test") {
                group = "verification"
                description = "Runs the Android host tests, the entry point every other module offers."
                dependsOn("testAndroidHostTest")
            }
        }
    }
}
