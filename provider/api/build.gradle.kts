plugins {
    alias(libs.plugins.orbin.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.immutable)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
