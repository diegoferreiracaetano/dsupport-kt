plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dsupport.cli.MainKt") }

dependencies {
    implementation(project(":adapters"))
    testImplementation(kotlin("test"))
    testImplementation(project(":testing"))
}
