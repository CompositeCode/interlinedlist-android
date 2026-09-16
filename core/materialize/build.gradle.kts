plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.interlinedlist.android.core.materialize"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

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

    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // No Room cache and no Compose: `POST /api/materialize` is a one-shot write
    // whose result is authoritative, and the preview/confirm UI is built by the
    // feature surfaces that open it.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    // The repository tests drive a real Retrofit/OkHttp stack against MockWebServer.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.kotlinx.serialization)
}
