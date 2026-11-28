@file:Suppress("UnstableApiUsage")

pluginManagement {
    // Without this block build-logic's own plugins (kotlin-dsl) come from the Gradle Plugin
    // Portal, which redirects their jars to Maven Central and so hits its rate limit; see
    // ../gradle/maven-central-mirror.txt. Evaluated before the rest of this file, so it reads the
    // mirror URL itself.
    val mirror = file("../gradle/maven-central-mirror.txt").readLines().map(String::trim).single { it.isNotEmpty() && !it.startsWith("#") }
    repositories {
        maven(mirror) { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}

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
