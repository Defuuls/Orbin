@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Orbin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Application
include(":app")

// Core layers
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

// Architecture layers
include(":domain")
include(":data")
include(":network")
include(":media")

// Provider abstraction + implementations
include(":provider:api")
include(":provider:vichan")
include(":provider:lynxchan")

// Feature modules.
include(":feature:home")
include(":feature:board")
include(":feature:thread")
include(":feature:settings")
include(":feature:history")
include(":feature:search")
include(":feature:gallery")
include(":feature:downloads")
include(":feature:onboarding")
