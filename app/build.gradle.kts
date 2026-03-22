plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.englishflow"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.englishflow"
        minSdk = 29
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.core.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.konfetti.xml)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.8")
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}