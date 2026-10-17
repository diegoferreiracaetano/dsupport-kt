pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "dsupport-kt"

include(":contract", ":core", ":testing", ":adapters", ":mcp-orders", ":mcp-tickets", ":cli", ":eval")
