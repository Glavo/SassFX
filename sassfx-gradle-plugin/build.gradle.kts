import java.util.jar.JarFile

plugins {
    id("com.gradle.plugin-publish")
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

val verifyPluginJar = tasks.register("verifyPluginJar") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies the Gradle plugin descriptor and artifact boundary."
    dependsOn(tasks.jar)
    inputs.file(tasks.jar.flatMap { it.archiveFile })

    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
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

            val forbiddenEntries = jar.entries().asSequence()
                .map { it.name }
                .filter { name ->
                    name.startsWith("org/glavo/sassfx/internal/")
                        || name == "org/glavo/sassfx/SassCompiler.class"
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
