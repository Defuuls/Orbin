plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.hilt)
}

android { namespace = "com.orbin.provider.wakaba" }

dependencies {
    api(project(":provider:api"))
    implementation(project(":network"))
    implementation(project(":core:common"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.immutable)
    implementation(libs.jsoup)
}
