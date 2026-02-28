plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.lhzkml.jasmine.core.config"
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
    api(project(":jasmine-core:prompt:prompt-executor"))
    api(project(":jasmine-core:agent:agent-tools"))
    api(project(":jasmine-core:agent:agent-observe"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
