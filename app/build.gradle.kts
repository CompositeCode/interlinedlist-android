plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // The blog mailing list's request/response bodies are @Serializable.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.interlinedlist.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.interlinedlist.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Internal modules
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    // Depended on so its Hilt modules join the app component. "Create from…" is
    // opened from the messages, lists and documents surfaces, so it has no
    // navigation entry of its own here.
    implementation(project(":core:materialize"))
    // Depended on so its Hilt modules join the app component: it registers this device
    // under Settings → Applications and contributes the sign-out deregistration. It
    // also provides the DeviceLabelProvider `:feature:auth` injects for `sync-token`.
    implementation(project(":core:appsettings"))

    // Features
    implementation(project(":feature:auth"))
    implementation(project(":feature:lists"))
    implementation(project(":feature:messages"))
    implementation(project(":feature:directmessages"))
    implementation(project(":feature:documents"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:organizations"))
    implementation(project(":feature:integrations"))
    // Depended on so its Hilt modules join the app component. The AI surfaces
    // themselves live in the feature modules that use them, so there is no
    // navigation entry here.
    implementation(project(":feature:ai"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // AndroidX runtime
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    // Custom Tabs: the blog is server-rendered with no public JSON endpoint, so it is
    // read in a themed Custom Tab rather than in-app.
    implementation(libs.androidx.browser)

    // Networking for the blog mailing list. It is a two-endpoint public API with no
    // cache and one screen, so it lives here beside the rest of the blog routing
    // rather than in a feature module of its own.
    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // WorkManager + Hilt worker factory (bootstraps the documents delta-sync @HiltWorker)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    // The blog subscription repository tests drive a real Retrofit/OkHttp stack
    // against MockWebServer, so the asserted request bodies are the app's own.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.kotlinx.serialization)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
