plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin module: cross-cutting utilities (result/error types, dispatcher
// abstraction, session token contract) with no Android dependencies.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
