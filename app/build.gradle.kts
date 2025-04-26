plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.pokeradvisor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pokeradvisor"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.opencv:opencv:4.10.0")
}