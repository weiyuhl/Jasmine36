plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.objectbox)
}

android {
    namespace = "com.lhzkml.jasmine.core.rag.objectbox"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    implementation(project(":jasmine-core:rag:rag-core"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
