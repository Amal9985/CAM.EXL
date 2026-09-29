plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.siteinspection"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.siteinspection"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Apache POI Excel
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // CameraX & Location
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coil Image Loader for Jetpack Compose (PASTE HERE)
    implementation("io.coil-kt:coil-compose:2.6.0")
}
