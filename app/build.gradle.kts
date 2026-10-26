plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.prabhat.photocraft"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prabhat.photocraft"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0-m5.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
