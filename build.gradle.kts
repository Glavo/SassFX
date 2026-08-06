import org.glavo.sassfx.build.VerifyModuleBoundariesTask
import org.glavo.sassfx.build.VerifyReleaseVersionTask
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.util.Properties

plugins {
    base
    id("com.gradle.plugin-publish") version "2.1.1" apply false
    id("org.jreleaser") version "1.25.0"
}

group = "org.glavo"

val versionProperties = Properties().apply {
    file("gradle/version.properties").inputStream().use(::load)
}
val baseVersion = versionProperties.getProperty("sassfxVersion")
    ?: error("gradle/version.properties must define sassfxVersion")

version = providers.gradleProperty("sassfxVersion")
    .orElse(providers.environmentVariable("SASSFX_VERSION"))
    .getOrElse("$baseVersion-SNAPSHOT")
description = "Pure Java Sass compiler with CSS, JavaFX CSS, and BSS backends."

jreleaser {
    configFile.set(layout.projectDirectory.file("jreleaser.yml"))
    dependsOnAssemble.set(false)
}

tasks.matching { it.name.startsWith("jreleaser") }.configureEach {
    doNotTrackState("JReleaser manages its own working directory and trace log.")
}

val verifyReleaseVersion = tasks.register<VerifyReleaseVersionTask>(
    "verifyReleaseVersion",
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that a release build uses a stable semantic version."
    releaseVersion.set(project.version.toString())
}

val cleanStagingRepository = tasks.register<Delete>(
    "cleanStagingRepository",
) {
    delete(layout.buildDirectory.dir("staging-repository"))
}

val moduleBoundarySources = files(
    fileTree("sassfx-cli/src/main/java") {
        include("**/*.java")
    },
    fileTree("sassfx-embedded/src/main/java") {
        include("**/*.java")
    },
    fileTree("sassfx-gradle-plugin/src/main/java") {
        include("**/*.java")
    },
)

val publicApiSources = fileTree(
    "sassfx-core/src/main/java/org/glavo/sassfx",
) {
    include("*.java")
}

val verifyModuleBoundaries = tasks.register<VerifyModuleBoundariesTask>(
    "verifyModuleBoundaries",
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies supported core and frontend API boundaries."
    sourceFiles.from(moduleBoundarySources)
    publicApiSourceFiles.from(publicApiSources)
    rootDirectory.set(layout.projectDirectory)
}

subprojects {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption(
            "Werror",
            true,
        )
    }

    pluginManager.withPlugin("maven-publish") {
        tasks.withType<PublishToMavenRepository>().configureEach {
            if (name.endsWith("ToLocalStagingRepository")) {
                dependsOn(cleanStagingRepository)
            }
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
    dependsOn(verifyModuleBoundaries)
    dependsOn(":sassfx-core:check")
    dependsOn(":sassfx-cli:check")
    dependsOn(":sassfx-embedded:check")
    dependsOn(":sassfx-gradle-plugin:check")
}

val stageMavenPublications = tasks.register("stageMavenPublications") {
    group = "publishing"
    description = "Stages every Maven publication for verification or deployment."
    dependsOn(":sassfx-core:publishAllPublicationsToLocalStagingRepository")
    dependsOn(":sassfx-cli:publishAllPublicationsToLocalStagingRepository")
    dependsOn(":sassfx-embedded:publishAllPublicationsToLocalStagingRepository")
    dependsOn(":sassfx-gradle-plugin:publishAllPublicationsToLocalStagingRepository")
}

tasks.register<Exec>("verifyPublishedConsumer") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies all staged artifacts from an isolated Gradle consumer."
    dependsOn(stageMavenPublications)
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

tasks.named("jreleaserDeploy") {
    dependsOn(stageMavenPublications)
    dependsOn(verifyReleaseVersion)
}

tasks.named("jreleaserRelease") {
    dependsOn(verifyReleaseVersion)
}

tasks.clean {
    dependsOn(":sassfx-core:clean")
    dependsOn(":sassfx-cli:clean")
    dependsOn(":sassfx-embedded:clean")
    dependsOn(":sassfx-gradle-plugin:clean")
}
