plugins {
    base
}

group = "org.glavo"
version = "0.1.0-SNAPSHOT"

tasks.assemble {
    dependsOn(":scssfx-core:assemble")
    dependsOn(":scssfx-cli:assemble")
    dependsOn(":scssfx-embedded:assemble")
    dependsOn(":scssfx-gradle-plugin:assemble")
}

tasks.check {
    dependsOn(":scssfx-core:check")
    dependsOn(":scssfx-cli:check")
    dependsOn(":scssfx-embedded:check")
    dependsOn(":scssfx-gradle-plugin:check")
}

tasks.clean {
    dependsOn(":scssfx-core:clean")
    dependsOn(":scssfx-cli:clean")
    dependsOn(":scssfx-embedded:clean")
    dependsOn(":scssfx-gradle-plugin:clean")
}
