plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.interlinedlist.android.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // Single source of truth for the API origin; overridable per build type later.
        buildConfigField("String", "BASE_URL", "\"https://interlinedlist.com/\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    api(libs.retrofit.core)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    api(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
}
