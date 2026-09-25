plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.hilt)
}

android {
    // Opt in: library modules ship no resources by default (see gradle.properties). The
    // watched-thread notification is user-facing text, and it was English built in code.
    androidResources {
        enable = true
    }

    namespace = "com.orbin.data"

    // MigrationTestHelper loads the exported schemas as Android assets, even under Robolectric.
    // The schema itself is `:storage`'s, shared with iOS.
    sourceSets {
        getByName("test") { assets.directories.add("$rootDir/storage/schemas") }
    }
}

dependencies {
    api(project(":domain"))
    // The shared Room schema, DAOs and database-only repositories. This module opens that database
    // (encrypted, with its migrations) and wires everything into Hilt.
    api(project(":storage"))
    implementation(project(":provider:api"))
    implementation(project(":network"))
    implementation(project(":core:common-android"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.sqlcipher.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // SQLCipher's native library cannot load under Robolectric, so the encrypted-open path is
    // only reachable from an instrumentation test on a real device or emulator.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
