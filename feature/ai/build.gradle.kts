plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.interlinedlist.android.feature.ai"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    // The shared authed Retrofit, safeApiCall, and InterlinedListApi (used to read
    // `customerStatus` when /api/ai/status omits its `subscriber` flag).
    implementation(project(":core:network"))

    // The AI wire format is only partly modelled: artifacts and the `context`
    // hint bag stay raw JsonObjects that the sibling AI surfaces decode, so the
    // type leaks into this module's public API and has to be `api`.
    api(libs.kotlinx.serialization.json)

    // This module has no Room cache: /suggest and /generate are live, one-shot
    // calls and /status is a cheap read, so there is nothing worth persisting.

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    // Repository tests hit a MockWebServer through the real Retrofit stack.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.core)
    testImplementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.okhttp.core)
    testImplementation(libs.kotlinx.serialization.json)

    // Instrumented / UI tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
