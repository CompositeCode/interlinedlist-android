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

rootProject.name = "InterlinedList"

// App entry point
include(":app")

// Core foundation modules (shared across all features)
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:datastore")

// Feature modules (added per roadmap phase)
include(":feature:auth")
