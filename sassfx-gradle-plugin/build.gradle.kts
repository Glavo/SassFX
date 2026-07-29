import org.glavo.sassfx.build.VerifyPluginJarTask
import org.glavo.sassfx.build.VerifyPluginPublicationTask
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.gradle.plugin-publish")
    id("com.gradleup.shadow") version "9.6.1"
}

group = rootProject.group
version = rootProject.version

dependencies {
    implementation(project(":sassfx-core"))

    compileOnly("org.jetbrains:annotations:26.1.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website = "https://github.com/Glavo/SassFX"
    vcsUrl = "https://github.com/Glavo/SassFX.git"
    plugins {
        create("sassfx") {
            id = "org.glavo.sassfx"
            implementationClass = "org.glavo.sassfx.gradle.SassFXPlugin"
            displayName = "SassFX"
            description = "Compiles Sass to CSS, JavaFX CSS, or JavaFX BSS."
            tags = listOf("sass", "scss", "css", "javafx")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.withType<Javadoc>().configureEach {
    javadocTool = javaToolchains.javadocToolFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    options.encoding = "UTF-8"
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE.txt" }
    }
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.sassfx.gradle",
        )
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    relocate(
        "org.glavo.sassfx",
        "org.glavo.sassfx.gradle.internal.compiler",
    ) {
        exclude("org.glavo.sassfx.gradle.**")
    }
    relocate(
        "com.google.gson",
        "org.glavo.sassfx.gradle.internal.thirdparty.gson",
    )
    relocate(
        "com.google.errorprone",
        "org.glavo.sassfx.gradle.internal.thirdparty.errorprone",
    )
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.sassfx.gradle",
        )
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "SassFX Gradle Plugin"
            description = "Gradle plugin for compiling Sass to CSS, JavaFX CSS, or BSS."
            inceptionYear = "2026"
            url = "https://github.com/Glavo/SassFX"
            licenses {
                license {
                    name = "Mozilla Public License 2.0"
                    url = "https://www.mozilla.org/MPL/2.0/"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = "glavo"
                    name = "Glavo"
                    url = "https://github.com/Glavo"
                }
            }
            scm {
                url = "https://github.com/Glavo/SassFX"
                connection = "scm:git:https://github.com/Glavo/SassFX.git"
                developerConnection = "scm:git:ssh://git@github.com/Glavo/SassFX.git"
            }
        }
    }
    repositories {
        maven {
            name = "localStaging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-repository"))
        }
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val verifyPluginJar = tasks.register<VerifyPluginJarTask>("verifyPluginJar") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies the Gradle plugin descriptor and artifact boundary."
    archiveFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}

val verifyPluginPublication = tasks.register<VerifyPluginPublicationTask>(
    "verifyPluginPublication",
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the published Gradle plugin is self-contained."
    dependsOn("generatePomFileForPluginMavenPublication")
    dependsOn("generateMetadataFileForPluginMavenPublication")
    pomFile.set(
        layout.buildDirectory.file(
            "publications/pluginMaven/pom-default.xml",
        ),
    )
    moduleMetadataFile.set(
        layout.buildDirectory.file(
            "publications/pluginMaven/module.json",
        ),
    )
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.check {
    dependsOn(verifyPluginJar)
    dependsOn(verifyPluginPublication)
}
