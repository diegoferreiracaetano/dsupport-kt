import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()
    // Mesmos alvos do core: os testes em commonTest do core usam este módulo e precisam compilar no Wasm também.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser { testTask { enabled = false } } }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
        }
        // No `main` da JVM, `kotlin.test.Test` só existe com o artefato do JUnit explícito.
        jvmMain.dependencies { api(kotlin("test-junit5")) }
    }
}
