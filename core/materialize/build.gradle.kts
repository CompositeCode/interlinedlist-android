plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.interlinedlist.android.core.materialize"
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
    // `customerStatus` / `CustomerStatus.isSubscriber` — the same subscriber flag
    // the rest of the app gates on.
    implementation(project(":core:model"))
    // ApiResult / AppError / DispatcherProvider. Materialize reuses the shared
    // error type so the feature modules can keep their existing
    // `AppError.toUserMessage()` / `isSubscriptionGate` helpers.
    implementation(project(":core:common"))
    // The shared authed Retrofit and `GET /api/user` (read by MaterializeGate).
    implementation(project(":core:network"))
    // The themed Compose surface the preview/confirm window is drawn on.
    implementation(project(":core:designsystem"))

    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // The preview/edit/confirm window lives here rather than in a feature module:
    // five entry points (messages, lists, rows, documents, selections) open the
    // same window, so re-implementing it per surface would let them drift apart.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // No Room cache: `POST /api/materialize` is a one-shot write whose result is
    // authoritative, so there is nothing to read back offline.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    // The repository tests drive a real Retrofit/OkHttp stack against MockWebServer.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.kotlinx.serialization)

    // Instrumented / UI tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
