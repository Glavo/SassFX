import org.glavo.sassfx.build.FailTask
import org.glavo.sassfx.build.VerifyCoreLibraryJarTask
import org.glavo.sassfx.build.VerifyReferenceIsolationTask
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    `maven-publish`
}

group = rootProject.group
version = rootProject.version

dependencies {
    compileOnly("org.jetbrains:annotations:26.1.0")

    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val javaFXOracleSourceSet = sourceSets.create("javaFXOracle")
val javaFX8OracleSourceSet = sourceSets.create("javaFX8Oracle")

val javaFXOracleVersions = linkedMapOf(
    17 to "17.0.20",
    18 to "18.0.2",
    23 to "23.0.2",
    25 to "25.0.4",
    26 to "26.0.2",
    27 to "27-ea+25",
)
val javaFXOracleRuntimes = javaFXOracleVersions.mapValues { (version, _) ->
    configurations.create("javaFX${version}OracleRuntime")
}
val javaFXOracleDirectory = providers.gradleProperty("javaFXOracleDirectory").orNull
val hostOperatingSystem = System.getProperty("os.name").lowercase()
val hostArchitecture = System.getProperty("os.arch").lowercase()
val openJfxPlatform = when {
    hostOperatingSystem.contains("win") && hostArchitecture.contains("aarch64") ->
        "win-aarch64"
    hostOperatingSystem.contains("win") -> "win"
    hostOperatingSystem.contains("mac") && hostArchitecture.contains("aarch64") ->
        "mac-aarch64"
    hostOperatingSystem.contains("mac") -> "mac"
    hostOperatingSystem.contains("linux") && hostArchitecture.contains("aarch64") ->
        "linux-aarch64"
    hostOperatingSystem.contains("linux") -> "linux"
    else -> throw GradleException(
        "No OpenJFX oracle classifier is configured for "
            + "$hostOperatingSystem/$hostArchitecture.",
    )
}

dependencies {
    add(
        javaFXOracleSourceSet.compileOnlyConfigurationName,
        "org.jetbrains:annotations:26.1.0",
    )
    add(
        javaFX8OracleSourceSet.compileOnlyConfigurationName,
        "org.jetbrains:annotations:26.1.0",
    )
    if (javaFXOracleDirectory == null) {
        add(
            javaFXOracleSourceSet.compileOnlyConfigurationName,
            "org.openjfx:javafx-base:17.0.20:$openJfxPlatform",
        )
        add(
            javaFXOracleSourceSet.compileOnlyConfigurationName,
            "org.openjfx:javafx-graphics:17.0.20:$openJfxPlatform",
        )
        for ((version, artifactVersion) in javaFXOracleVersions) {
            val runtime = javaFXOracleRuntimes.getValue(version)
            runtime(
                "org.openjfx:javafx-base:$artifactVersion:$openJfxPlatform",
            )
            runtime(
                "org.openjfx:javafx-graphics:$artifactVersion:$openJfxPlatform",
            )
        }
    } else {
        val oracleRoot = file(javaFXOracleDirectory)
        val javaFX17Files = files(
            oracleRoot.resolve("17/javafx-base.jar"),
            oracleRoot.resolve("17/javafx-graphics.jar"),
        )
        add(javaFXOracleSourceSet.compileOnlyConfigurationName, javaFX17Files)
        for (version in javaFXOracleVersions.keys) {
            javaFXOracleRuntimes.getValue(version)(
                files(
                    oracleRoot.resolve("$version/javafx-base.jar"),
                    oracleRoot.resolve("$version/javafx-graphics.jar"),
                ),
            )
        }
    }
}

javaFXOracleSourceSet.compileClasspath += sourceSets.main.get().output
javaFXOracleSourceSet.runtimeClasspath += sourceSets.main.get().output

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

tasks.named<JavaCompile>(javaFX8OracleSourceSet.compileJavaTaskName) {
    options.release = 8
}

tasks.test {
    systemProperty("sassfx.test.expectedVersion", project.version.toString())
    useJUnitPlatform {
        excludeTags("sass-spec")
    }
}

val sassSpec = tasks.register<Test>("sassSpec") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the pinned Sass specification corpus and project fixtures."
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("sass-spec")
    }

    val reportDirectory = layout.buildDirectory.dir("reports/sass-spec")
    systemProperty("sassfx.sassSpecReportDir", reportDirectory.get().asFile.absolutePath)
    outputs.dir(reportDirectory)
}

tasks.withType<Javadoc>().configureEach {
    javadocTool = javaToolchains.javadocToolFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    options.encoding = "UTF-8"
}

val generateVersionProperties =
    tasks.register<WriteProperties>("generateVersionProperties") {
        destinationFile.set(
            layout.buildDirectory.file(
                "generated/sassfx-version/sassfx-version.properties",
            ),
        )
        property("version", project.version.toString())
        encoding = "UTF-8"
        lineSeparator = "\n"
    }

tasks.processResources {
    exclude("org/glavo/sassfx/sassfx-version.properties")
    from(generateVersionProperties) {
        into("org/glavo/sassfx")
    }
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE.txt" }
    }
    from(rootProject.layout.projectDirectory.file("THIRD-PARTY-NOTICES.md")) {
        into("META-INF")
    }
    from(rootProject.layout.projectDirectory.file("UPSTREAM.md")) {
        into("META-INF")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.glavo.sassfx",
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "SassFX Core"
                description = "Pure Java Sass compiler with CSS, JavaFX CSS, and BSS backends."
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

val verifyCoreLibraryJar = tasks.register<VerifyCoreLibraryJarTask>(
    "verifyCoreLibraryJar",
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the core library JAR contains no CLI implementation or entry point."
    archiveFile.set(tasks.jar.flatMap { it.archiveFile })
}

val javaFXOracleLauncherVersions = mapOf(
    17 to 17,
    18 to 17,
    23 to 21,
    25 to 23,
    26 to 24,
    27 to 25,
)
val javaFXOracleLaunchers = javaFXOracleLauncherVersions.mapValues { (_, javaVersion) ->
    javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}
val javaFXOracleJavaHome = providers.gradleProperty("javaFXOracleJavaHome")
    .orNull

val verifyJavaFXCssOracleTasks = javaFXOracleVersions.keys.associateWith { version ->
    tasks.register<JavaExec>("verifyJavaFX${version}CssOracle") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description =
            "Validates JavaFX $version CSS compatibility against the pinned OpenJFX parser."
        dependsOn(javaFXOracleSourceSet.classesTaskName)
        val versionJavaHome = providers
            .gradleProperty("javaFX${version}OracleJavaHome")
            .orNull
        val selectedJavaHome = versionJavaHome ?: javaFXOracleJavaHome
        if (selectedJavaHome == null) {
            javaLauncher.set(javaFXOracleLaunchers.getValue(version))
        } else {
            val javaExecutable =
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            executable(
                file(selectedJavaHome)
                    .resolve("bin/$javaExecutable")
                    .absolutePath,
            )
        }
        classpath = files(
            javaFXOracleSourceSet.output,
            sourceSets.main.get().runtimeClasspath,
        )
        jvmArgs(
            "--module-path",
            javaFXOracleRuntimes.getValue(version).asPath,
            "--add-modules",
            "javafx.graphics",
        )
        mainClass.set("org.glavo.sassfx.oracle.JavaFXCssOracle")
        args(version.toString())
    }
}

val javaFX8OracleDirectory = layout.buildDirectory.dir("tmp/javafx8-oracle")
val generateJavaFX8OracleInputs =
    tasks.register<JavaExec>("generateJavaFX8OracleInputs") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Generates CSS and BSS inputs for the isolated JavaFX 8 oracle."
        dependsOn(javaFXOracleSourceSet.classesTaskName)
        javaLauncher.set(javaFXOracleLaunchers.getValue(17))
        classpath = files(
            javaFXOracleSourceSet.output,
            sourceSets.main.get().runtimeClasspath,
        )
        mainClass.set("org.glavo.sassfx.oracle.JavaFX8OracleInputGenerator")
        args(javaFX8OracleDirectory.get().asFile.absolutePath)
        outputs.files(
            javaFX8OracleDirectory.map { it.file("fixture.css") },
            javaFX8OracleDirectory.map { it.file("actual.bss") },
        )
    }

val javaFX8JavaHome = providers.gradleProperty("javaFX8OracleJavaHome").orNull
val verifyJavaFX8CssOracle =
    if (javaFX8JavaHome == null) {
        tasks.register<FailTask>("verifyJavaFX8CssOracle") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Validates JavaFX 8 BSS against a configured JavaFX 8 runtime."
            failureMessage.set(
                "verifyJavaFX8CssOracle requires -PjavaFX8OracleJavaHome="
                    + "<a Java 8 JDK containing JavaFX 8>.",
            )
        }
    } else {
        tasks.register<Exec>("verifyJavaFX8CssOracle") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Validates JavaFX 8 BSS against a configured JavaFX 8 runtime."
            dependsOn(generateJavaFX8OracleInputs)
            dependsOn(javaFX8OracleSourceSet.classesTaskName)
            val javaExecutable =
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            executable(
                file(javaFX8JavaHome)
                    .resolve("bin/$javaExecutable")
                    .absolutePath,
            )
            args(
                "-Djava.awt.headless=true",
                "-cp",
                javaFX8OracleSourceSet.output.classesDirs.asPath,
                "org.glavo.sassfx.oracle.JavaFX8OracleHelper",
                javaFX8OracleDirectory.get().asFile.absolutePath,
            )
        }
    }

tasks.register("verifyJavaFXCssOracles") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the isolated JavaFX 17 through 27 CSS parser oracle matrix."
    dependsOn(verifyJavaFXCssOracleTasks.values)
}

tasks.register("verifyAllJavaFXCssOracles") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the isolated JavaFX 8 and JavaFX 17 through 27 oracle matrix."
    dependsOn(verifyJavaFX8CssOracle)
    dependsOn(verifyJavaFXCssOracleTasks.values)
}

val referenceSensitiveFiles = fileTree(rootProject.layout.projectDirectory) {
    include("sassfx-core/src/**")
    include("sassfx-cli/src/**")
    include("sassfx-embedded/src/**")
    include("sassfx-gradle-plugin/src/**")
    include("buildSrc/src/**")
    include("buildSrc/*.gradle.kts")
    include("gradle/**/*.gradle.kts")
    include("gradle/**/*.java")
    include("gradle/**/*.properties")
    include("gradle/**/*.scss")
    include("gradle/**/*.xml")
    include(".github/**")
    include("*.gradle.kts")
    include("sassfx-core/*.gradle.kts")
    include("sassfx-cli/*.gradle.kts")
    include("sassfx-embedded/*.gradle.kts")
    include("sassfx-gradle-plugin/*.gradle.kts")
    include("*.md")
}

val verifyReferenceIsolation = tasks.register<VerifyReferenceIsolationTask>(
    "verifyReferenceIsolation",
) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that committed project inputs do not reference a local upstream checkout."
    sourceFiles.from(referenceSensitiveFiles)
    rootDirectory.set(rootProject.layout.projectDirectory)
}

tasks.check {
    dependsOn(verifyCoreLibraryJar)
    dependsOn(verifyReferenceIsolation)
    dependsOn(sassSpec)
}
