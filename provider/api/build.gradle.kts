plugins {
    alias(libs.plugins.orbin.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
