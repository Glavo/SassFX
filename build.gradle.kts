import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("com.vanniktech.maven.publish.base") version "0.37.0" apply false
    id("com.gradle.plugin-publish") version "2.1.1" apply false
}

group = "org.glavo"
version = providers.gradleProperty("sassfxVersion")
    .orElse(providers.environmentVariable("SASSFX_VERSION"))
    .getOrElse("0.1.0-SNAPSHOT")

val verifyReleaseVersion = tasks.register("verifyReleaseVersion") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that a release build uses a stable semantic version."

    doLast {
        val releaseVersion = version.toString()
        if (!Regex("""\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?""").matches(releaseVersion)
            || releaseVersion.endsWith("-SNAPSHOT")
        ) {
            throw GradleException(
                "A release requires -PsassfxVersion=<stable semantic version>; "
                    + "received '$releaseVersion'.",
            )
        }
    }
}

val cleanLocalStagingRepository = tasks.register<Delete>(
    "cleanLocalStagingRepository",
) {
    delete(layout.buildDirectory.dir("staging-repository"))
}

subprojects {
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption(
            "Werror",
            true,
        )
    }

    pluginManager.withPlugin("maven-publish") {
        tasks.matching {
            it.name == "publishAllPublicationsToLocalStagingRepository"
        }.configureEach {
            dependsOn(cleanLocalStagingRepository)
        }
    }
}

tasks.assemble {
    dependsOn(":sassfx-core:assemble")
    dependsOn(":sassfx-cli:assemble")
    dependsOn(":sassfx-embedded:assemble")
    dependsOn(":sassfx-gradle-plugin:assemble")
}

tasks.check {
    dependsOn(":sassfx-core:check")
    dependsOn(":sassfx-cli:check")
    dependsOn(":sassfx-embedded:check")
    dependsOn(":sassfx-gradle-plugin:check")
}

tasks.register("verifyLocalPublications") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Publishes every Maven artifact to an isolated local repository."
    dependsOn(":sassfx-core:publishAllPublicationsToLocalStagingRepository")
    dependsOn(":sassfx-cli:publishAllPublicationsToLocalStagingRepository")
    dependsOn(":sassfx-embedded:publishAllPublicationsToLocalStagingRepository")
    dependsOn(":sassfx-gradle-plugin:publishAllPublicationsToLocalStagingRepository")
}

tasks.register<Exec>("verifyPublishedConsumer") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies all staged artifacts from an isolated Gradle consumer."
    dependsOn("verifyLocalPublications")
    workingDir(layout.projectDirectory.dir("gradle/verification/consumer"))

    val wrapperName = if (System.getProperty("os.name").startsWith("Windows")) {
        "gradlew.bat"
    } else {
        "gradlew"
    }
    val wrapper = layout.projectDirectory.file(wrapperName).asFile.absolutePath
    val repository = layout.buildDirectory.dir("staging-repository")
        .get().asFile.absolutePath
    commandLine(
        wrapper,
        "--no-daemon",
        "-g",
        layout.projectDirectory.dir(".gradle-user-home")
            .asFile.absolutePath,
        "-PsassfxRepository=$repository",
        "-PsassfxVersion=${project.version}",
        "--warning-mode",
        "all",
        "check",
    )
}

tasks.clean {
    dependsOn(":sassfx-core:clean")
    dependsOn(":sassfx-cli:clean")
    dependsOn(":sassfx-embedded:clean")
    dependsOn(":sassfx-gradle-plugin:clean")
}
