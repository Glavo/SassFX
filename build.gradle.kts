import java.util.zip.ZipFile

plugins {
    `java-library`
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "org.glavo"
version = "0.1.0-SNAPSHOT"

dependencies {
    compileOnly("org.jetbrains:annotations:26.1.0")

    implementation("com.fasterxml.jackson.core:jackson-core:2.22.1")
    implementation("info.picocli:picocli:4.7.7")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withJavadocJar()
    withSourcesJar()
}

application {
    mainClass = "org.glavo.scssfx.cli.ScssfxMain"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.test {
    useJUnitPlatform {
        excludeTags("sass-spec")
    }
}

val sassSpec by tasks.registering(Test::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the curated Sass specification compatibility fixtures."
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("sass-spec")
    }

    val reportDirectory = layout.buildDirectory.dir("reports/sass-spec")
    systemProperty("scssfx.sassSpecReportDir", reportDirectory.get().asFile.absolutePath)
    outputs.dir(reportDirectory)
}

tasks.withType<Javadoc>().configureEach {
    javadocTool = javaToolchains.javadocToolFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    options.encoding = "UTF-8"
}

tasks.processResources {
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE.txt" }
    }
    from(layout.projectDirectory.file("THIRD-PARTY-NOTICES.md")) {
        into("META-INF")
    }
    from(layout.projectDirectory.file("UPSTREAM.md")) {
        into("META-INF")
    }
}

tasks.jar {
    archiveClassifier = "plain"
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.scssfx",
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
    relocate("com.fasterxml.jackson", "org.glavo.scssfx.internal.thirdparty.jackson")
    relocate("picocli", "org.glavo.scssfx.internal.thirdparty.picocli")
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.scssfx",
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
    description = "Verifies that the distributable JAR contains no JavaFX, FFI, or native content."
    dependsOn(tasks.shadowJar)
    inputs.file(tasks.shadowJar.flatMap { it.archiveFile })

    doLast {
        val archive = tasks.shadowJar.get().archiveFile.get().asFile
        val forbiddenEntryPatterns = listOf(
            Regex("(^|/)javafx/", RegexOption.IGNORE_CASE),
            Regex("(^|/)com/sun/javafx/", RegexOption.IGNORE_CASE),
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

        ZipFile(archive).use { zipFile ->
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

val referenceSensitiveFiles = fileTree(layout.projectDirectory) {
    include("src/**")
    include("*.gradle.kts")
    include("*.md")
}

val verifyReferenceIsolation = tasks.register("verifyReferenceIsolation") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that committed project inputs do not reference a local upstream checkout."
    inputs.files(referenceSensitiveFiles)

    doLast {
        val forbiddenReferences = listOf(
            "external" + "/",
            "external" + "\\",
            layout.projectDirectory.asFile.absolutePath,
        )
        val violations = referenceSensitiveFiles.files.flatMap { file ->
            file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
                forbiddenReferences.firstOrNull { reference ->
                    line.contains(reference, ignoreCase = true)
                }?.let { reference ->
                    "${file.relativeTo(layout.projectDirectory.asFile)}:${index + 1}: ${reference}"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Project inputs contain forbidden local references:\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.check {
    dependsOn(verifyShadedJar)
    dependsOn(verifyReferenceIsolation)
    dependsOn(smokeTestShadedCli)
    dependsOn(sassSpec)
}

tasks.register("printTestRuntimeClasspath") {
    doLast {
        println(sourceSets["test"].runtimeClasspath.asPath)
    }
}
