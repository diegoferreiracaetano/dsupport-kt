import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser { testTask { enabled = false } } }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies { api(kotlin("test-junit5")) }
    }
}
