plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.hilt)
}

android {
    namespace = "com.orbin.data"
}

dependencies {
    api(project(":domain"))
    implementation(project(":provider:api"))
    implementation(project(":network"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
