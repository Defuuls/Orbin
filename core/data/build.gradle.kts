plugins {
    alias(libs.plugins.orbin.kmp.android)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.orbin.core.data"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            api(project(":storage"))
            api(project(":provider:api"))
            api(project(":core:common"))
            api(project(":core:model"))
            api(project(":core:network"))
            api(libs.androidx.paging.common)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            api(libs.androidx.datastore.preferences.core)
            api("com.squareup.okio:okio:3.9.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
