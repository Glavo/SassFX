plugins {
    base
}

group = "org.glavo"
version = "0.1.0-SNAPSHOT"

tasks.assemble {
    dependsOn(":scssfx-core:assemble")
    dependsOn(":scssfx-cli:assemble")
    dependsOn(":scssfx-embedded:assemble")
}

tasks.check {
    dependsOn(":scssfx-core:check")
    dependsOn(":scssfx-cli:check")
    dependsOn(":scssfx-embedded:check")
}

tasks.clean {
    dependsOn(":scssfx-core:clean")
    dependsOn(":scssfx-cli:clean")
    dependsOn(":scssfx-embedded:clean")
}
