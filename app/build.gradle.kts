plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    buildFeatures {
        compose = true
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

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Coroutines（UI 层需要协程来调用框架）
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
