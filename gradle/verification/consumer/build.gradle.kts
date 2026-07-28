plugins {
    java
    id("org.glavo.sassfx")
}

val sassfxVersion = providers.gradleProperty("sassfxVersion").get()
val sassfxCli = configurations.create("sassfxCli")
val sassfxEmbedded = configurations.create("sassfxEmbedded")

dependencies {
    implementation("org.glavo:sassfx-core:$sassfxVersion")
    compileOnly("org.jetbrains:annotations:26.1.0")
    sassfxCli("org.glavo:sassfx-cli:$sassfxVersion")
    sassfxEmbedded("org.glavo:sassfx-embedded:$sassfxVersion")
}

sassfx {
    target = "css/javafx@21"
    style = "compressed"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val verifyCli = tasks.register<JavaExec>("verifyCli") {
    classpath = sassfxCli
    mainClass = "org.glavo.sassfx.cli.SassFXMain"
    args("--version")
}

val verifyEmbedded = tasks.register<JavaExec>("verifyEmbedded") {
    classpath = sassfxEmbedded
    mainClass = "org.glavo.sassfx.embedded.SassFXEmbeddedMain"
    args("--version")
}

tasks.check {
    dependsOn(tasks.compileScss)
    dependsOn(verifyCli)
    dependsOn(verifyEmbedded)
}
