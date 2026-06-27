plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.hilt)
}

android {
    namespace = "com.orbin.domain"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":provider:api"))

    api(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
