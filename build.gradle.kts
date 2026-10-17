plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Módulos KMP não têm a tarefa `test`: este agregador roda os testes JVM de todos os módulos,
// KMP (`jvmTest`) e JVM puros (`test`). O Wasm só é compilado, nunca testado (ver CI).
tasks.register("test") {
    group = "verification"
    description = "Roda os testes JVM de todos os módulos."
    dependsOn(provider {
        subprojects.flatMap { sub ->
            sub.tasks.matching { it.name == "jvmTest" || it.name == "test" }.toList()
        }
    })
}

// Todos os módulos rodam os testes no JUnit Platform (JUnit 5).
subprojects {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
