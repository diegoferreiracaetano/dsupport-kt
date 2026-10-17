import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser { testTask { enabled = false } } }

    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.serialization.json) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
