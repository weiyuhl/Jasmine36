plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lhzkml.jasmine.core.agent.dex"
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
    packaging {
        resources {
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    api(project(":jasmine-core:agent:agent-tools"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // smali/baksmali/dexlib2 for DEX parsing and editing
    implementation("org.smali:dexlib2:2.5.2")
    implementation("org.smali:smali:2.5.2")
    implementation("org.smali:baksmali:2.5.2")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
