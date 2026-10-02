plugins {
    alias(libs.plugins.orbin.kmp.library)
    id("com.google.devtools.ksp")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            api(project(":storage"))
            api(project(":provider:api"))
            api(project(":core:common"))
            api(project(":core:model"))
            api(libs.androidx.paging.common)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlin.inject.runtime)
        }
    }
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.kotlin.inject.compiler)
    }
}
