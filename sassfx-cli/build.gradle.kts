import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.maven.MavenPublication
import java.util.zip.ZipFile

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
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.1")
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
    relocate("com.fasterxml.jackson", "org.glavo.sassfx.internal.thirdparty.jackson")
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

val verifyShadedJar = tasks.register("verifyShadedJar") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the distributable JAR contains no JavaFX, FFI, or native content."
    dependsOn(tasks.shadowJar)
    inputs.file(tasks.shadowJar.flatMap { it.archiveFile })

    doLast {
        val archive = tasks.shadowJar.get().archiveFile.get().asFile
        val forbiddenEntryPatterns = listOf(
            Regex("(^|/)javafx/", RegexOption.IGNORE_CASE),
            Regex("(^|/)com/sun/javafx/", RegexOption.IGNORE_CASE),
            Regex("^com/google/protobuf/.*"),
            Regex("^com/sass_lang/embedded_protocol/.*"),
            Regex(".*\\.(a|dll|dylib|exe|jnilib|lib|node|wasm)$", RegexOption.IGNORE_CASE),
            Regex(".*\\.so(?:\\.\\d+)*$", RegexOption.IGNORE_CASE),
            Regex(".*\\.(dart|js|mjs|cjs)$", RegexOption.IGNORE_CASE),
        )
        val forbiddenClassReferences = listOf(
            "javafx/",
            "com/sun/javafx/",
            "java/lang/foreign/",
            "jdk/incubator/foreign/",
            "com/sun/jna/",
            "com/sun/jnr/",
            "jnr/ffi/",
            "com/kenai/jffi/",
        )
        val forbiddenEntries = mutableListOf<String>()
        val forbiddenReferences = mutableListOf<String>()
        val requiredEntries = listOf(
            "org/glavo/sassfx/embedded/EmbeddedCompiler.class",
            "org/glavo/sassfx/internal/thirdparty/protobuf/Message.class",
            "org/glavo/sassfx/internal/thirdparty/embedded_protocol/InboundMessage.class",
        )

        ZipFile(archive).use { zipFile ->
            val missingEntries = requiredEntries.filter { entry ->
                zipFile.getEntry(entry) == null
            }
            if (missingEntries.isNotEmpty()) {
                throw GradleException(
                    "The distributable JAR is missing embedded-protocol entries: " +
                        missingEntries.joinToString(),
                )
            }
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) {
                    continue
                }

                if (forbiddenEntryPatterns.any { pattern -> pattern.matches(entry.name) }) {
                    forbiddenEntries += entry.name
                }

                if (entry.name.endsWith(".class")) {
                    val classContents = zipFile.getInputStream(entry).use { input ->
                        input.readBytes().toString(Charsets.ISO_8859_1)
                    }
                    forbiddenReferences.addAll(
                        forbiddenClassReferences
                            .filter { reference -> classContents.contains(reference) }
                            .map { reference -> "${entry.name}: ${reference}" },
                    )
                }
            }
        }

        if (forbiddenEntries.isNotEmpty()) {
            throw GradleException(
                "The distributable JAR contains forbidden entries: " +
                    forbiddenEntries.joinToString(),
            )
        }
        if (forbiddenReferences.isNotEmpty()) {
            throw GradleException(
                "The distributable JAR contains forbidden class references: " +
                    forbiddenReferences.joinToString(),
            )
        }
    }
}

val java17Launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
}

val verifyJava17Toolchain = tasks.register("verifyJava17Toolchain") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that a Java 17 launcher is available for runtime compatibility checks."

    doLast {
        runCatching { java17Launcher.get() }.getOrElse { cause ->
            throw GradleException(
                "Java 17 is required for the shaded CLI smoke test. Configure a Java 17 toolchain.",
                cause,
            )
        }
    }
}

val smokeTestShadedCli = tasks.register<JavaExec>("smokeTestShadedCli") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the shaded CLI version command on Java 17."
    dependsOn(tasks.shadowJar)
    dependsOn(verifyJava17Toolchain)
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
    dependsOn(verifyJava17Toolchain)
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
