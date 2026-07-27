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

rootProject.name = "scssfx"

include("scssfx-core")
include("scssfx-cli")
include("scssfx-embedded")
