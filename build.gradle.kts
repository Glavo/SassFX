plugins {
    base
}

group = "org.glavo"
version = "0.1.0-SNAPSHOT"

tasks.assemble {
    dependsOn(":sassfx-core:assemble")
    dependsOn(":sassfx-cli:assemble")
    dependsOn(":sassfx-embedded:assemble")
    dependsOn(":sassfx-gradle-plugin:assemble")
}

tasks.check {
    dependsOn(":sassfx-core:check")
    dependsOn(":sassfx-cli:check")
    dependsOn(":sassfx-embedded:check")
    dependsOn(":sassfx-gradle-plugin:check")
}

tasks.clean {
    dependsOn(":sassfx-core:clean")
    dependsOn(":sassfx-cli:clean")
    dependsOn(":sassfx-embedded:clean")
    dependsOn(":sassfx-gradle-plugin:clean")
}
