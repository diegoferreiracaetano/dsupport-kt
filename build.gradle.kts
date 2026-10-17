plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register("test") {
    group = "verification"
    description = "Runs the JVM tests of every module."
    dependsOn(provider {
        subprojects.flatMap { sub ->
            sub.tasks.matching { it.name == "jvmTest" || it.name == "test" }.toList()
        }
    })
}

subprojects {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
