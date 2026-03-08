pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:0.10.10")
            }
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Jasmine"
include(":app")
include(":jasmine-core:prompt:prompt-model")
include(":jasmine-core:prompt:prompt-llm")
include(":jasmine-core:prompt:prompt-executor")
include(":jasmine-core:conversation:conversation-storage")
include(":jasmine-core:agent:agent-tools")
include(":jasmine-core:agent:agent-observe")
include(":jasmine-core:agent:agent-graph")
include(":jasmine-core:agent:agent-planner")
include(":jasmine-core:agent:agent-mcp")
include(":jasmine-core:agent:agent-a2a")
include(":jasmine-core:agent:agent-runtime")
include(":jasmine-core:config:config-manager")
include(":jasmine-core:rag:rag-core")
include(":jasmine-core:rag:rag-objectbox")
include(":jasmine-core:rag:rag-embedding-api")
