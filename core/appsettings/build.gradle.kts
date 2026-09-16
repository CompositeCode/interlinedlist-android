plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.interlinedlist.android.core.appsettings"
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
    // ApiResult / AppError / DispatcherProvider, plus the two cross-module contracts
    // this module implements: SessionTeardownTask and DeviceLabelProvider.
    implementation(project(":core:common"))
    // The shared authed Retrofit (base URL + bearer interceptor) and safeApiCall.
    implementation(project(":core:network"))

    implementation(libs.retrofit.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // ViewModel only (no Compose in this module): the signed-in shell in `:app` drives
    // the registration lifecycle through a `hiltViewModel()`, exactly as it drives the
    // push-token lifecycle.
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // No Room cache: the device registry is a tiny write-mostly registration, and the
    // one piece of state worth keeping (this install's device id) is a single string.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    // The repository tests drive a real Retrofit/OkHttp stack against MockWebServer.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.kotlinx.serialization)
}
