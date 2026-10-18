plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dsupport.server.MainKt") }

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

dependencies {
    implementation(project(":cli"))
    implementation(project(":core"))
    implementation(project(":contract"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.server.test.host)
}
