import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.util.jar.JarFile

@DisableCachingByDefault(because = "Verification tasks have no outputs.")
abstract class VerifyPluginJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val archive = archiveFile.get().asFile
        JarFile(archive).use { jar ->
            val descriptor = jar.getEntry(
                "META-INF/gradle-plugins/org.glavo.sassfx.properties",
            ) ?: throw GradleException(
                "The Gradle plugin JAR has no org.glavo.sassfx descriptor.",
            )
            val descriptorText = jar.getInputStream(descriptor)
                .bufferedReader(Charsets.ISO_8859_1)
                .use { it.readText() }
            if (!descriptorText.contains(
                    "implementation-class=org.glavo.sassfx.gradle.SassFXPlugin",
                )
            ) {
                throw GradleException(
                    "The Gradle plugin descriptor has an unexpected implementation class.",
                )
            }

            val requiredEntries = listOf(
                "org/glavo/sassfx/gradle/SassFXPlugin.class",
                "org/glavo/sassfx/gradle/SassFXCompile.class",
                "org/glavo/sassfx/gradle/internal/compiler/SassCompiler.class",
                "org/glavo/sassfx/gradle/internal/compiler/sassfx-version.properties",
                "org/glavo/sassfx/gradle/internal/thirdparty/gson/stream/JsonReader.class",
                "org/glavo/sassfx/gradle/internal/thirdparty/errorprone/annotations/CheckReturnValue.class",
            )
            val missingEntries = requiredEntries.filter { entry ->
                jar.getEntry(entry) == null
            }
            if (missingEntries.isNotEmpty()) {
                throw GradleException(
                    "The Gradle plugin JAR is missing required entries: "
                        + missingEntries.joinToString(),
                )
            }

            val forbiddenEntries = jar.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { name ->
                    (name.startsWith("org/glavo/sassfx/")
                        && !name.startsWith("org/glavo/sassfx/gradle/"))
                        || name.startsWith("com/google/errorprone/")
                        || name.startsWith("com/google/gson/")
                        || name.startsWith("org/gradle/")
                        || name.startsWith("groovy/")
                        || name.startsWith("org/codehaus/groovy/")
                        || name.startsWith("kotlin/")
                        || name.startsWith("javax/inject/")
                        || name.startsWith("org/slf4j/")
                        || name.startsWith("javafx/")
                        || name.startsWith("com/sun/javafx/")
                        || Regex(
                            ".*\\.(a|dll|dylib|exe|jnilib|lib|node|so|wasm)$",
                            RegexOption.IGNORE_CASE,
                        ).matches(name)
                }
                .toList()
            if (forbiddenEntries.isNotEmpty()) {
                throw GradleException(
                    "The Gradle plugin JAR contains forbidden entries: "
                        + forbiddenEntries.joinToString(),
                )
            }
        }
    }
}

@DisableCachingByDefault(because = "Verification tasks have no outputs.")
abstract class VerifyPluginPublicationTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pomFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val moduleMetadataFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val pom = pomFile.get().asFile.readText(Charsets.UTF_8)
        if (pom.contains("<dependencies>")) {
            throw GradleException(
                "The Gradle plugin POM exposes dependencies instead of "
                    + "publishing a self-contained Shadow JAR.",
            )
        }

        val moduleMetadata = moduleMetadataFile.get()
            .asFile
            .readText(Charsets.UTF_8)
        if (!moduleMetadata.contains("\"name\": \"shadowRuntimeElements\"")) {
            throw GradleException(
                "The Gradle plugin module metadata has no shadowed runtime variant.",
            )
        }
        if (moduleMetadata.contains("\"dependencies\":")) {
            throw GradleException(
                "The Gradle plugin shadowed runtime variant exposes dependencies.",
            )
        }
    }
}

plugins {
    id("com.gradle.plugin-publish")
    id("com.gradleup.shadow") version "9.6.1"
    id("com.vanniktech.maven.publish")
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val taskTemporaryDirectory = layout.buildDirectory.dir("tmp/$name")
    systemProperty(
        "java.io.tmpdir",
        taskTemporaryDirectory.get().asFile.absolutePath,
    )
    doFirst {
        taskTemporaryDirectory.get().asFile.mkdirs()
    }
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(project.group.toString(), project.name, project.version.toString())
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

publishing {
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
