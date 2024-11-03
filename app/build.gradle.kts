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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.gson)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-video:$1.3.4")
    implementation("androidx.camera:camera-view:$1.3.4")
    implementation("androidx.camera:camera-extensions:$1.3.4")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("androidx.core:core-splashscreen:1.0.1")

}