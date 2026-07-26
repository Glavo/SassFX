import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipFile

plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

dependencies {
    compileOnly("org.jetbrains:annotations:26.1.0")

    implementation("com.fasterxml.jackson.core:jackson-core:2.22.1")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains:annotations:26.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val javaFxOracleSourceSet = sourceSets.create("javaFxOracle")

val javaFx17OracleRuntime = configurations.create("javaFx17OracleRuntime")
val javaFx27OracleRuntime = configurations.create("javaFx27OracleRuntime")
val javaFxOracleDirectory = providers.gradleProperty("javaFxOracleDirectory").orNull
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
        javaFxOracleSourceSet.compileOnlyConfigurationName,
        "org.jetbrains:annotations:26.1.0",
    )
    if (javaFxOracleDirectory == null) {
        add(
            javaFxOracleSourceSet.compileOnlyConfigurationName,
            "org.openjfx:javafx-graphics:17.0.20:$openJfxPlatform",
        )
        javaFx17OracleRuntime(
            "org.openjfx:javafx-base:17.0.20:$openJfxPlatform",
        )
        javaFx17OracleRuntime(
            "org.openjfx:javafx-graphics:17.0.20:$openJfxPlatform",
        )
        javaFx27OracleRuntime(
            "org.openjfx:javafx-base:27-ea+25:$openJfxPlatform",
        )
        javaFx27OracleRuntime(
            "org.openjfx:javafx-graphics:27-ea+25:$openJfxPlatform",
        )
    } else {
        val oracleRoot = file(javaFxOracleDirectory)
        val javaFx17Files = files(
            oracleRoot.resolve("17/javafx-base.jar"),
            oracleRoot.resolve("17/javafx-graphics.jar"),
        )
        val javaFx27Files = files(
            oracleRoot.resolve("27/javafx-base.jar"),
            oracleRoot.resolve("27/javafx-graphics.jar"),
        )
        add(javaFxOracleSourceSet.compileOnlyConfigurationName, javaFx17Files)
        javaFx17OracleRuntime(javaFx17Files)
        javaFx27OracleRuntime(javaFx27Files)
    }
}

javaFxOracleSourceSet.compileClasspath += sourceSets.main.get().output
javaFxOracleSourceSet.runtimeClasspath += sourceSets.main.get().output

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

tasks.withType<Test>().configureEach {
    val taskTemporaryDirectory = layout.buildDirectory.dir("tmp/$name")
    systemProperty(
        "java.io.tmpdir",
        taskTemporaryDirectory.get().asFile.absolutePath,
    )
    doFirst {
        taskTemporaryDirectory.get().asFile.mkdirs()
    }
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
            "Automatic-Module-Name" to "org.glavo.scssfx",
        )
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val verifyCoreLibraryJar = tasks.register("verifyCoreLibraryJar") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the core library JAR contains no CLI implementation or entry point."
    dependsOn(tasks.jar)
    inputs.file(tasks.jar.flatMap { it.archiveFile })

    doLast {
        val archive = tasks.jar.get().archiveFile.get().asFile
        ZipFile(archive).use { zipFile ->
            val forbiddenEntries = zipFile.entries().asSequence()
                .map { entry -> entry.name }
                .filter { name ->
                    name.startsWith("org/glavo/scssfx/cli/")
                        || name.startsWith("picocli/")
                        || name.contains("/picocli/")
                }
                .toList()
            if (forbiddenEntries.isNotEmpty()) {
                throw GradleException(
                    "The core library JAR contains CLI entries: " +
                        forbiddenEntries.joinToString(),
                )
            }

            val manifestEntry = zipFile.getEntry("META-INF/MANIFEST.MF")
                ?: throw GradleException("The core library JAR has no manifest.")
            val manifest = zipFile.getInputStream(manifestEntry).use(::Manifest)
            val mainClass = manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS)
            if (mainClass != null) {
                throw GradleException(
                    "The core library JAR declares an application entry point: $mainClass",
                )
            }
            val moduleName = manifest.mainAttributes.getValue("Automatic-Module-Name")
            if (moduleName != "org.glavo.scssfx") {
                throw GradleException(
                    "The core library JAR declares an unexpected module name: $moduleName",
                )
            }
        }
    }
}

val java17Launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
}

val java25Launcher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}
val javaFx27OracleJavaHome =
    providers.gradleProperty("javaFx27OracleJavaHome").orNull

val verifyJavaFx17CssOracle = tasks.register<JavaExec>("verifyJavaFx17CssOracle") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validates JavaFX 17 CSS compatibility against the pinned OpenJFX parser."
    dependsOn(javaFxOracleSourceSet.classesTaskName)
    javaLauncher.set(java17Launcher)
    classpath = files(
        javaFxOracleSourceSet.output,
        sourceSets.main.get().runtimeClasspath,
    )
    jvmArgs(
        "--upgrade-module-path",
        javaFx17OracleRuntime.asPath,
        "--add-modules",
        "javafx.graphics",
    )
    mainClass.set("org.glavo.scssfx.oracle.JavaFxCssOracle")
    args("17")
}

val verifyJavaFx27CssOracle = tasks.register<JavaExec>("verifyJavaFx27CssOracle") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validates JavaFX 27 CSS compatibility against the pinned OpenJFX parser."
    dependsOn(javaFxOracleSourceSet.classesTaskName)
    if (javaFx27OracleJavaHome == null) {
        javaLauncher.set(java25Launcher)
    } else {
        val javaExecutable =
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        executable(
            file(javaFx27OracleJavaHome)
                .resolve("bin/$javaExecutable")
                .absolutePath,
        )
    }
    classpath = files(
        javaFxOracleSourceSet.output,
        sourceSets.main.get().runtimeClasspath,
    )
    jvmArgs(
        "--upgrade-module-path",
        javaFx27OracleRuntime.asPath,
        "--add-modules",
        "javafx.graphics",
    )
    mainClass.set("org.glavo.scssfx.oracle.JavaFxCssOracle")
    args("27")
}

tasks.register("verifyJavaFxCssOracles") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the isolated JavaFX 17 and JavaFX 27 CSS parser oracles."
    dependsOn(verifyJavaFx17CssOracle)
    dependsOn(verifyJavaFx27CssOracle)
}

val referenceSensitiveFiles = fileTree(rootProject.layout.projectDirectory) {
    include("scssfx-core/src/**")
    include("scssfx-cli/src/**")
    include("*.gradle.kts")
    include("scssfx-core/*.gradle.kts")
    include("scssfx-cli/*.gradle.kts")
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
            rootProject.layout.projectDirectory.asFile.absolutePath,
        )
        val violations = referenceSensitiveFiles.files.flatMap { file ->
            file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
                forbiddenReferences.firstOrNull { reference ->
                    line.contains(reference, ignoreCase = true)
                }?.let { reference ->
                    "${file.relativeTo(rootProject.layout.projectDirectory.asFile)}:" +
                        "${index + 1}: $reference"
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

tasks.check {
    dependsOn(verifyCoreLibraryJar)
    dependsOn(verifyReferenceIsolation)
    dependsOn(sassSpec)
}

tasks.register("printTestRuntimeClasspath") {
    doLast {
        println(sourceSets["test"].runtimeClasspath.asPath)
    }
}
