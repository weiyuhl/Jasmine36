plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lhzkml.jasmine.core.prompt.mnn"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    ndkVersion = "26.3.11579264"
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    api(project(":jasmine-core:prompt:prompt-model"))
    api(project(":jasmine-core:prompt:prompt-llm"))
    
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    
    // Ktor Client for model download
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // DocumentFile for SAF
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
