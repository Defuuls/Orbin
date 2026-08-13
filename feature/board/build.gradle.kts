plugins {
    alias(libs.plugins.orbin.android.feature)
}

android {
    namespace = "com.orbin.feature.board"
}

dependencies {
    implementation(project(":media"))
    implementation(libs.androidx.paging.compose)

    // asSnapshot(), for asserting on what the catalog's PagingData actually holds.
    testImplementation(libs.androidx.paging.testing)
}
