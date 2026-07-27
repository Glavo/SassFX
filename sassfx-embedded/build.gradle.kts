import java.util.jar.JarFile

plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = rootProject.group
version = rootProject.version

dependencies {
    implementation(project(":sassfx-core"))
    implementation("de.larsgrefer.sass:sass-embedded-protocol:4.4.0")

    compileOnly("org.jetbrains:annotations:26.1.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.glavo.sassfx.embedded.SassFXEmbeddedMain"
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

tasks.jar {
    archiveClassifier = "plain"
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.sassfx.embedded",
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
    relocate(
        "com.google.protobuf",
        "org.glavo.sassfx.internal.thirdparty.protobuf",
    )
    relocate(
        "com.sass_lang.embedded_protocol",
        "org.glavo.sassfx.internal.thirdparty.embedded_protocol",
    )
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.sassfx.embedded",
            "Main-Class" to application.mainClass.get(),
        )
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val verifyShadedJar = tasks.register("verifyShadedJar") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the embedded compiler contains no JavaFX, FFI, or native content."
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
            "org/glavo/sassfx/embedded/SassFXEmbeddedMain.class",
            "org/glavo/sassfx/embedded/EmbeddedCompiler.class",
            "org/glavo/sassfx/internal/thirdparty/protobuf/Message.class",
            "org/glavo/sassfx/internal/thirdparty/embedded_protocol/InboundMessage.class",
        )

        JarFile(archive).use { zipFile ->
            val mainClass = zipFile.manifest.mainAttributes
                .getValue("Main-Class")
            if (mainClass != application.mainClass.get()) {
                throw GradleException(
                    "The embedded compiler has an unexpected Main-Class: $mainClass",
                )
            }
            val missingEntries = requiredEntries.filter { entry ->
                zipFile.getEntry(entry) == null
            }
            if (missingEntries.isNotEmpty()) {
                throw GradleException(
                    "The embedded compiler is missing required entries: " +
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
                    val contents = zipFile.getInputStream(entry).use { input ->
                        input.readBytes().toString(Charsets.ISO_8859_1)
                    }
                    forbiddenReferences.addAll(
                        forbiddenClassReferences
                            .filter { reference -> contents.contains(reference) }
                            .map { reference -> "${entry.name}: ${reference}" },
                    )
                }
            }
        }

        if (forbiddenEntries.isNotEmpty()) {
            throw GradleException(
                "The embedded compiler contains forbidden entries: " +
                    forbiddenEntries.joinToString(),
            )
        }
        if (forbiddenReferences.isNotEmpty()) {
            throw GradleException(
                "The embedded compiler contains forbidden class references: " +
                    forbiddenReferences.joinToString(),
            )
        }
    }
}

val java17Launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
}

val smokeTestShadedEmbedded = tasks.register<JavaExec>("smokeTestShadedEmbedded") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the shaded embedded compiler version command on Java 17."
    dependsOn(tasks.shadowJar)
    javaLauncher.set(java17Launcher)
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile })
    mainClass.set(application.mainClass)
    args("--version")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.check {
    dependsOn(verifyShadedJar)
    dependsOn(smokeTestShadedEmbedded)
}
