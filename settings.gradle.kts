@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    // Evaluated before the rest of this file, so it reads the mirror URL itself.
    val mirror = file("gradle/maven-central-mirror.txt").readLines().map(String::trim).single { it.isNotEmpty() && !it.startsWith("#") }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven(mirror) { name = "MavenCentralMirror" }
        mavenCentral()
        gradlePluginPortal()
    }
}

// See gradle/maven-central-mirror.txt for why the mirror is listed before Maven Central.
val mavenCentralMirror: String =
    file("gradle/maven-central-mirror.txt").readLines().map(String::trim).single { it.isNotEmpty() && !it.startsWith("#") }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(mavenCentralMirror) { name = "MavenCentralMirror" }
        mavenCentral()
    }
}

rootProject.name = "Orbin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":app-ios")
include(":benchmark")

include(":core:common")
include(":core:common-android")
include(":core:data")
include(":core:model")
include(":core:graph")
include(":core:network")
include(":core:designsystem")
include(":core:ui")
include(":core:reader")
include(":core:testing")

include(":domain")
include(":data")
include(":network")
include(":storage")
include(":media")

include(":provider:api")
include(":provider:vichan")
include(":provider:lynxchan")

include(":feature:home")
include(":feature:board")
include(":feature:thread")
include(":feature:settings")
include(":feature:search")
include(":feature:browser")
include(":feature:gallery")
include(":feature:downloads")
include(":feature:onboarding")

include(":ui-next")
