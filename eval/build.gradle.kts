plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dsupport.eval.EvalRunnerKt") }

dependencies {
    implementation(project(":adapters"))
    testImplementation(kotlin("test"))
    testImplementation(project(":testing"))
}
