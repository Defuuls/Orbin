import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import com.orbin.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Room shared by Android and iOS, on top of [KmpAndroidLibraryConventionPlugin]: the Room runtime
 * in `commonMain`, the Room compiler run by KSP once per target, and schemas exported in-repo so
 * migrations can be tested deterministically.
 *
 * How the database is opened stays per platform — the module's Android consumer supplies its own
 * open helper and migrations, iOS a SQLite driver — so this only shares the schema and its DAOs.
 */
class KmpRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("orbin.kmp.android")
                apply("androidx.room")
                apply("com.google.devtools.ksp")
            }

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                compilerOptions {
                    // Room builds the database outside Android through an `expect object` that
                    // KSP supplies the `actual` for, a pattern the compiler still calls Beta.
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("room-runtime").get())
                }
            }

            // One Room compiler run per target: each platform gets its own generated database.
            dependencies {
                val compiler = libs.findLibrary("room-compiler").get()
                listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, compiler) }
            }
        }
    }
}
