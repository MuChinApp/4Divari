pluginManagement {
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

rootProject.name = "4Divari"

// Core — foundation modules shared by features
include(":core:common")
include(":core:environment")
include(":core:network")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":core:analytics")
include(":core:auth")

// Features — customer-facing shells (Phase 1: navigation placeholders)
include(":feature:home")
include(":feature:search")
include(":feature:saved")
include(":feature:profile")
include(":feature:auth")

// App — composition root
include(":app")
