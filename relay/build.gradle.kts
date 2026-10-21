plugins {
    alias(libs.plugins.orbin.jvm.library)
    application
}

// A self-hostable relay that forwards end-to-end encrypted nearby-sync frames between two paired
// Orbin devices. See docs/relay.md. `./gradlew :relay:installDist` builds a runnable server.
application {
    mainClass.set("com.orbin.relay.MainKt")
    applicationName = "orbin-relay"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}
