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
        }
    }
}
