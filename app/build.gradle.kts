plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.lhzkml.jasmine"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lhzkml.jasmine"
        minSdk = 26
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xnested-type-aliases")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Jasmine 框架核心
    implementation(project(":jasmine-core:prompt:prompt-executor"))
    implementation(project(":jasmine-core:conversation:conversation-storage"))
    implementation(project(":jasmine-core:agent:agent-tools"))
    implementation(project(":jasmine-core:agent:agent-observe"))
    implementation(project(":jasmine-core:agent:agent-graph"))
    implementation(project(":jasmine-core:agent:agent-planner"))
    implementation(project(":jasmine-core:agent:agent-mcp"))
    implementation(project(":jasmine-core:agent:agent-runtime"))
    implementation(project(":jasmine-core:config:config-manager"))

    // Coroutines（UI 层需要协程来调用框架）
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
