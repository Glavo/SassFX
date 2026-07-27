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
val javaFx8OracleSourceSet = sourceSets.create("javaFx8Oracle")

val javaFxOracleVersions = linkedMapOf(
    17 to "17.0.20",
    18 to "18.0.2",
    23 to "23.0.2",
    25 to "25.0.4",
    26 to "26.0.2",
    27 to "27-ea+25",
)
val javaFxOracleRuntimes = javaFxOracleVersions.mapValues { (version, _) ->
    configurations.create("javaFx${version}OracleRuntime")
}
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
    add(
        javaFx8OracleSourceSet.compileOnlyConfigurationName,
        "org.jetbrains:annotations:26.1.0",
    )
    if (javaFxOracleDirectory == null) {
        add(
            javaFxOracleSourceSet.compileOnlyConfigurationName,
            "org.openjfx:javafx-base:17.0.20:$openJfxPlatform",
        )
        add(
            javaFxOracleSourceSet.compileOnlyConfigurationName,
            "org.openjfx:javafx-graphics:17.0.20:$openJfxPlatform",
        )
        for ((version, artifactVersion) in javaFxOracleVersions) {
            val runtime = javaFxOracleRuntimes.getValue(version)
            runtime(
                "org.openjfx:javafx-base:$artifactVersion:$openJfxPlatform",
            )
            runtime(
                "org.openjfx:javafx-graphics:$artifactVersion:$openJfxPlatform",
            )
        }
    } else {
        val oracleRoot = file(javaFxOracleDirectory)
        val javaFx17Files = files(
            oracleRoot.resolve("17/javafx-base.jar"),
            oracleRoot.resolve("17/javafx-graphics.jar"),
        )
        add(javaFxOracleSourceSet.compileOnlyConfigurationName, javaFx17Files)
        for (version in javaFxOracleVersions.keys) {
            javaFxOracleRuntimes.getValue(version)(
                files(
                    oracleRoot.resolve("$version/javafx-base.jar"),
                    oracleRoot.resolve("$version/javafx-graphics.jar"),
                ),
            )
        }
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

tasks.named<JavaCompile>(javaFx8OracleSourceSet.compileJavaTaskName) {
    options.release = 8
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
    systemProperty("sassfx.sassSpecReportDir", reportDirectory.get().asFile.absolutePath)
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
            "Automatic-Module-Name" to "org.glavo.sassfx",
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
                    name.startsWith("org/glavo/sassfx/cli/")
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
            if (moduleName != "org.glavo.sassfx") {
                throw GradleException(
                    "The core library JAR declares an unexpected module name: $moduleName",
                )
            }
        }
    }
}

val javaFxOracleLauncherVersions = mapOf(
    17 to 17,
    18 to 17,
    23 to 21,
    25 to 23,
    26 to 24,
    27 to 25,
)
val javaFxOracleLaunchers = javaFxOracleLauncherVersions.mapValues { (_, javaVersion) ->
    javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}
val javaFxOracleJavaHome = providers.gradleProperty("javaFxOracleJavaHome")
    .orElse(providers.gradleProperty("javaFx27OracleJavaHome"))
    .orNull

val verifyJavaFxCssOracleTasks = javaFxOracleVersions.keys.associateWith { version ->
    tasks.register<JavaExec>("verifyJavaFx${version}CssOracle") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description =
            "Validates JavaFX $version CSS compatibility against the pinned OpenJFX parser."
        dependsOn(javaFxOracleSourceSet.classesTaskName)
        val versionJavaHome = providers
            .gradleProperty("javaFx${version}OracleJavaHome")
            .orNull
        val selectedJavaHome = versionJavaHome ?: javaFxOracleJavaHome
        if (selectedJavaHome == null) {
            javaLauncher.set(javaFxOracleLaunchers.getValue(version))
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
            javaFxOracleSourceSet.output,
            sourceSets.main.get().runtimeClasspath,
        )
        jvmArgs(
            "--module-path",
            javaFxOracleRuntimes.getValue(version).asPath,
            "--add-modules",
            "javafx.graphics",
        )
        mainClass.set("org.glavo.sassfx.oracle.JavaFxCssOracle")
        args(version.toString())
    }
}

val javaFx8OracleDirectory = layout.buildDirectory.dir("tmp/javafx8-oracle")
val generateJavaFx8OracleInputs =
    tasks.register<JavaExec>("generateJavaFx8OracleInputs") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Generates CSS and BSS inputs for the isolated JavaFX 8 oracle."
        dependsOn(javaFxOracleSourceSet.classesTaskName)
        javaLauncher.set(javaFxOracleLaunchers.getValue(17))
        classpath = files(
            javaFxOracleSourceSet.output,
            sourceSets.main.get().runtimeClasspath,
        )
        mainClass.set("org.glavo.sassfx.oracle.JavaFx8OracleInputGenerator")
        args(javaFx8OracleDirectory.get().asFile.absolutePath)
        outputs.files(
            javaFx8OracleDirectory.map { it.file("fixture.css") },
            javaFx8OracleDirectory.map { it.file("actual.bss") },
        )
    }

val javaFx8JavaHome = providers.gradleProperty("javaFx8OracleJavaHome").orNull
val verifyJavaFx8CssOracle = tasks.register<Exec>("verifyJavaFx8CssOracle") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validates JavaFX 8 BSS against a configured JavaFX 8 runtime."
    dependsOn(generateJavaFx8OracleInputs)
    dependsOn(javaFx8OracleSourceSet.classesTaskName)
    if (javaFx8JavaHome == null) {
        executable("java")
        doFirst {
            throw GradleException(
                "verifyJavaFx8CssOracle requires -PjavaFx8OracleJavaHome="
                    + "<a Java 8 JDK containing JavaFX 8>.",
            )
        }
    } else {
        val javaExecutable =
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        executable(
            file(javaFx8JavaHome)
                .resolve("bin/$javaExecutable")
                .absolutePath,
        )
    }
    args(
        "-Djava.awt.headless=true",
        "-cp",
        javaFx8OracleSourceSet.output.classesDirs.asPath,
        "org.glavo.sassfx.oracle.JavaFx8OracleHelper",
        javaFx8OracleDirectory.get().asFile.absolutePath,
    )
}

tasks.register("verifyJavaFxCssOracles") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the isolated JavaFX 17 through 27 CSS parser oracle matrix."
    dependsOn(verifyJavaFxCssOracleTasks.values)
}

tasks.register("verifyAllJavaFxCssOracles") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the isolated JavaFX 8 and JavaFX 17 through 27 oracle matrix."
    dependsOn(verifyJavaFx8CssOracle)
    dependsOn(verifyJavaFxCssOracleTasks.values)
}

val referenceSensitiveFiles = fileTree(rootProject.layout.projectDirectory) {
    include("sassfx-core/src/**")
    include("sassfx-cli/src/**")
    include("sassfx-embedded/src/**")
    include("sassfx-gradle-plugin/src/**")
    include("*.gradle.kts")
    include("sassfx-core/*.gradle.kts")
    include("sassfx-cli/*.gradle.kts")
    include("sassfx-embedded/*.gradle.kts")
    include("sassfx-gradle-plugin/*.gradle.kts")
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
