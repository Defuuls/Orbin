@file:Suppress("UnstableApiUsage")

// See ../gradle/maven-central-mirror.txt for why the mirror is listed before Maven Central.
val mavenCentralMirror: String =
    file("../gradle/maven-central-mirror.txt").readLines().map(String::trim).single { it.isNotEmpty() && !it.startsWith("#") }

dependencyResolutionManagement {
    repositories {
        google()
        maven(mavenCentralMirror) { name = "MavenCentralMirror" }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
