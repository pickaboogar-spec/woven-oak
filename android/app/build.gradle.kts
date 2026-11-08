plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.damonini.wovenoak"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.damonini.wovenoak"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.1.0-beta.10"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Deliberately framework Android + Kotlin stdlib only:
    // no AndroidX, no Compose, no third-party dependencies.
    implementation(kotlin("stdlib"))
}
