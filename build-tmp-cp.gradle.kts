tasks.register("writeTestCp") {
    doLast {
        val cp = sourceSets["test"].runtimeClasspath.asPath
        file("build/tmp/test-runtime.cp").writeText(cp)
    }
}
