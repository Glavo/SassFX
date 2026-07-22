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
    useJUnitPlatform()
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
    description = "Verifies that the distributable JAR contains no native or JavaFX runtime content."
    dependsOn(tasks.shadowJar)
    inputs.file(tasks.shadowJar.flatMap { it.archiveFile })

    doLast {
        val archive = tasks.shadowJar.get().archiveFile.get().asFile
        val forbiddenEntries = zipTree(archive).matching {
            include("**/javafx/**")
            include("**/com/sun/javafx/**")
            include("**/*.dll")
            include("**/*.dylib")
            include("**/*.jnilib")
            include("**/*.so")
            include("**/*.dart")
            include("**/*.js")
            include("**/*.mjs")
            include("**/*.cjs")
            include("**/*.wasm")
        }.files

        if (forbiddenEntries.isNotEmpty()) {
            throw GradleException(
                "The distributable JAR contains forbidden entries: " +
                    forbiddenEntries.joinToString { it.name },
            )
        }
    }
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
}