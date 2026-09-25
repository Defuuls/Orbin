plugins {
    alias(libs.plugins.orbin.kmp.library)
}

// Use cases and repository contracts, shared with iOS. Hilt bindings for the use cases live in
// :data (DomainModule), so this module stays free of any DI framework.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            api(project(":provider:api"))

            api(libs.androidx.paging.common)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.mockk)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
