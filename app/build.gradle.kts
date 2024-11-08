plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "hku.cs.comp3330.section1a2024.group19.gymmygo"
    compileSdk = 34

    defaultConfig {
        applicationId = "hku.cs.comp3330.section1a2024.group19.gymmygo"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildToolsVersion = "34.0.0"
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.camera.view)
    implementation(libs.camera.lifecycle)
    implementation(libs.vision.common)
    implementation(libs.pose.detection.common)
    implementation(libs.pose.detection)
    testImplementation(libs.junit)
    implementation (libs.navigation.fragment)
    implementation (libs.navigation.ui)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.gson)
    implementation ("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")

    implementation("androidx.activity:activity:1.6.1")

    implementation("androidx.camera:camera-core:1.1.0")
    implementation("androidx.camera:camera-camera2:1.1.0")
    implementation("androidx.camera:camera-lifecycle:1.1.0")
    implementation("androidx.camera:camera-view:1.0.0")
    implementation("androidx.camera:camera-video:1.0.0")
}