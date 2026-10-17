plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies { api(kotlin("test-junit5")) }
    }
}
