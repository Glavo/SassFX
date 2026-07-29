plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains:annotations:26.1.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}
