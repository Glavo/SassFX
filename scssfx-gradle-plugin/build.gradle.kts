import java.util.jar.JarFile

plugins {
    `java-gradle-plugin`
}

group = rootProject.group
version = rootProject.version

dependencies {
    implementation(project(":scssfx-core"))

    compileOnly("org.jetbrains:annotations:26.1.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("scssfx") {
            id = "org.glavo.scssfx"
            implementationClass = "org.glavo.scssfx.gradle.ScssfxPlugin"
            displayName = "SCSSFX"
            description = "Compiles Sass to CSS, JavaFX CSS, or JavaFX BSS."
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
            "Automatic-Module-Name" to "org.glavo.scssfx.gradle",
        )
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val verifyPluginJar = tasks.register("verifyPluginJar") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies the Gradle plugin descriptor and artifact boundary."
    dependsOn(tasks.jar)
    inputs.file(tasks.jar.flatMap { it.archiveFile })

    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        JarFile(archive).use { jar ->
            val descriptor = jar.getEntry(
                "META-INF/gradle-plugins/org.glavo.scssfx.properties",
            ) ?: throw GradleException(
                "The Gradle plugin JAR has no org.glavo.scssfx descriptor.",
            )
            val descriptorText = jar.getInputStream(descriptor)
                .bufferedReader(Charsets.ISO_8859_1)
                .use { it.readText() }
            if (!descriptorText.contains(
                    "implementation-class=org.glavo.scssfx.gradle.ScssfxPlugin",
                )
            ) {
                throw GradleException(
                    "The Gradle plugin descriptor has an unexpected implementation class.",
                )
            }

            val forbiddenEntries = jar.entries().asSequence()
                .map { it.name }
                .filter { name ->
                    name.startsWith("org/glavo/scssfx/internal/")
                        || name == "org/glavo/scssfx/SassCompiler.class"
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

tasks.check {
    dependsOn(verifyPluginJar)
}
