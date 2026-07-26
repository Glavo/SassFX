plugins {
    base
}

group = "org.glavo"
version = "0.1.0-SNAPSHOT"

tasks.assemble {
    dependsOn(":scssfx-core:assemble")
    dependsOn(":scssfx-cli:assemble")
}

tasks.check {
    dependsOn(":scssfx-core:check")
    dependsOn(":scssfx-cli:check")
}

tasks.clean {
    dependsOn(":scssfx-core:clean")
    dependsOn(":scssfx-cli:clean")
}
