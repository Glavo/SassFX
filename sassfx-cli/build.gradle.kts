import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.glavo.sassfx.build.VerifyShadedCompilerJarTask
import org.gradle.api.publish.maven.MavenPublication

plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
    id("com.vanniktech.maven.publish.base")
}

group = rootProject.group
version = rootProject.version

dependencies {
    implementation(project(":sassfx-core"))
    implementation(project(":sassfx-embedded"))
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("info.picocli:picocli:4.7.7")

    compileOnly("org.jetbrains:annotations:26.1.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.glavo.sassfx.cli.SassFXMain"
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

tasks.jar {
    archiveClassifier = "plain"
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.sassfx.cli",
            "Main-Class" to application.mainClass.get(),
        )
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    relocate("com.google.gson", "org.glavo.sassfx.internal.thirdparty.gson")
    relocate(
        "com.google.errorprone",
        "org.glavo.sassfx.internal.thirdparty.errorprone",
    )
    relocate("picocli", "org.glavo.sassfx.internal.thirdparty.picocli")
    relocate("com.google.protobuf", "org.glavo.sassfx.internal.thirdparty.protobuf")
    relocate(
        "com.sass_lang.embedded_protocol",
        "org.glavo.sassfx.internal.thirdparty.embedded_protocol",
    )
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.sassfx.cli",
            "Main-Class" to application.mainClass.get(),
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
            artifact(tasks.named<Jar>("sourcesJar"))
            artifact(tasks.named<Jar>("javadocJar"))
            pom {
                name = "SassFX CLI"
                description = "Standalone command-line Sass compiler implemented in Java."
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
    }
    repositories {
        maven {
            name = "localStaging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-repository"))
        }
    }
}

extensions.configure<MavenPublishBaseExtension> {
    publishToMavenCentral()
    signAllPublications()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val verifyShadedJar = tasks.register<VerifyShadedCompilerJarTask>(
    "verifyShadedJar",
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the distributable JAR contains no JavaFX, FFI, or native content."
    archiveFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    expectedMainClass.set(application.mainClass)
    artifactName.set("The distributable JAR")
    requiredEntries.set(
        listOf(
            "org/glavo/sassfx/embedded/EmbeddedCompiler.class",
            "org/glavo/sassfx/internal/thirdparty/errorprone/annotations/CheckReturnValue.class",
            "org/glavo/sassfx/internal/thirdparty/gson/stream/JsonReader.class",
            "org/glavo/sassfx/internal/thirdparty/protobuf/Message.class",
            "org/glavo/sassfx/internal/thirdparty/embedded_protocol/InboundMessage.class",
        ),
    )
}

val java17Launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
}

val smokeTestShadedCli = tasks.register<JavaExec>("smokeTestShadedCli") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the shaded CLI version command on Java 17."
    dependsOn(tasks.shadowJar)
    javaLauncher.set(java17Launcher)
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
    mainClass.set(application.mainClass)
    args("--version")
}

val smokeTestShadedEmbeddedCli = tasks.register<JavaExec>(
    "smokeTestShadedEmbeddedCli",
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the shaded CLI embedded version command on Java 17."
    dependsOn(tasks.shadowJar)
    javaLauncher.set(java17Launcher)
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
    mainClass.set(application.mainClass)
    args("--embedded", "--version")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.check {
    dependsOn(verifyShadedJar)
    dependsOn(smokeTestShadedCli)
    dependsOn(smokeTestShadedEmbeddedCli)
}
