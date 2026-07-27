import org.apache.tools.ant.DirectoryScanner

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

DirectoryScanner.addDefaultExclude("**/.gitignore")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "sassfx"

include("sassfx-core")
include("sassfx-cli")
include("sassfx-embedded")
include("sassfx-gradle-plugin")
