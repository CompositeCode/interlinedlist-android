plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin module: platform-independent domain models shared by every layer.
kotlin {
    jvmToolchain(17)
}
