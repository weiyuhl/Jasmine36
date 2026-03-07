import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.objectbox)
    id("com.google.android.gms.oss-licenses-plugin")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
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

        buildConfigField("String", "JASMINE_CORE_VERSION", "\"1.0\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"]!!.toString()
                keyAlias = keystoreProperties["keyAlias"]!!.toString()
                keyPassword = keystoreProperties["keyPassword"]!!.toString()
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    ndkVersion = "26.3.11579264"
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
    implementation(project(":jasmine-core:rag:rag-core"))
    implementation(project(":jasmine-core:rag:rag-objectbox"))
    implementation(project(":jasmine-core:rag:rag-embedding-api"))
    implementation(project(":jasmine-core:proot:proot-environment"))

    // Markdown 渲染（底层使用 CommonMark，与 Claude App 相同解析引擎）
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")

    // 数学公式渲染（与 Claude App 完全相同的库）
    implementation("com.github.gregcockroft:AndroidMath:v1.1.0") {
        exclude(group = "com.google.guava", module = "guava")
    }
    
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Ktor Client (MNN 模型市场网络请求)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation("androidx.compose.material3:material3")
    debugImplementation(libs.compose.ui.tooling)

    // Coroutines（UI 层需要协程来调用框架）
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // DocumentFile（导入/导出模型时访问 SAF 目录）
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
