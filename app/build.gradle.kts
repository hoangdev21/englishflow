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
        
        // Load Groq API settings from local.properties
        val localPropertiesFile = rootProject.file("local.properties")
        val groqApiKey = if (localPropertiesFile.exists()) {
            localPropertiesFile.readLines()
                .find { it.trim().startsWith("GROQ_API_KEY=") }
                ?.substringAfter("=")
                ?.trim()
                .orEmpty()
        } else {
            ""
        }
        val groqModel = if (localPropertiesFile.exists()) {
            localPropertiesFile.readLines()
                .find { it.trim().startsWith("GROQ_MODEL=") }
                ?.substringAfter("=")
                ?.trim()
                ?.ifEmpty { "meta-llama/llama-4-scout-17b-16e-instruct" }
                ?: "meta-llama/llama-4-scout-17b-16e-instruct"
        } else {
            "meta-llama/llama-4-scout-17b-16e-instruct"
        }
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
        buildConfigField("String", "GROQ_MODEL", "\"$groqModel\"")
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
    
    buildFeatures {
        buildConfig = true
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
    // Camera
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    // HTTP + JSON (for Gemini Vision API REST calls)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}