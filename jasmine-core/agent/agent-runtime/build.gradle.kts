plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.lhzkml.jasmine.core.agent.runtime"
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
    api(project(":jasmine-core:agent:agent-tools"))
    api(project(":jasmine-core:agent:agent-observe"))
    api(project(":jasmine-core:agent:agent-graph"))
    api(project(":jasmine-core:agent:agent-planner"))
    api(project(":jasmine-core:agent:agent-mcp"))
    api(project(":jasmine-core:config:config-manager"))
    api(project(":jasmine-core:conversation:conversation-storage"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
