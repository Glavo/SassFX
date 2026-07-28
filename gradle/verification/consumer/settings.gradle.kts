pluginManagement {
    val sassfxVersion = providers.gradleProperty("sassfxVersion").get()
    repositories {
        maven {
            url = uri(providers.gradleProperty("sassfxRepository").get())
        }
        gradlePluginPortal()
    }
    plugins {
        id("org.glavo.sassfx") version sassfxVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(providers.gradleProperty("sassfxRepository").get())
        }
        mavenCentral()
    }
}

rootProject.name = "sassfx-publication-consumer"
