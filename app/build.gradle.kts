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
    implementation (libs.pose.detection.accurate)


    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.camera:camera-core:1.2.2")
    implementation("androidx.camera:camera-camera2:1.2.2")
    implementation("androidx.camera:camera-lifecycle:1.2.2")
    implementation("androidx.camera:camera-video:1.2.2")

    implementation("androidx.camera:camera-view:1.2.2")
    implementation("androidx.camera:camera-extensions:1.2.2")

    // If you want to use the base sdk
    implementation("com.google.mlkit:pose-detection:18.0.0-beta3")

    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0") {
            because("kotlin-stdlib-jdk7 is now a part of kotlin-stdlib")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0") {
            because("kotlin-stdlib-jdk8 is now a part of kotlin-stdlib")
        }
    }
}