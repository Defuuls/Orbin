plugins {
    alias(libs.plugins.orbin.kmp.android)
    id("com.google.devtools.ksp")
}

// The object graph shared by Android and iOS, wired at compile time by kotlin-inject. Each platform
// supplies what only it can make (the HTTP engine, the opened database, the DataStore, a dispatcher)
// and gets back the same providers, stores and repositories. Android's Hilt graph reads its
// bindings through `SharedGraphModule` in :app; iOS builds it in `AppGraph`.
kotlin {
    android {
        namespace = "com.orbin.graph"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":storage"))
            api(project(":core:data"))
            api(project(":provider:api"))
            implementation(project(":provider:vichan"))
            implementation(project(":provider:lynxchan"))
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlin.inject.runtime)
            // OrbinDatabase is a RoomDatabase, which :storage keeps as an implementation detail.
            implementation(libs.room.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// One kotlin-inject run per target: each gets its own generated component and `createSharedGraph`.
dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.kotlin.inject.compiler)
    }
}
