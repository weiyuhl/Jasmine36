# MNN 本地模型模块

## 概述

`prompt-mnn` 模块提供了基于 MNN（阿里开源深度学习推理框架）的本地 LLM 推理能力。

## 架构

```
jasmine-core/prompt/prompt-mnn/
├── src/main/
│   ├── cpp/                          # C++ JNI 层
│   │   ├── mnn_jni.cpp              # JNI 桥接实现
│   │   ├── CMakeLists.txt           # CMake 构建配置
│   │   └── third_party/             # MNN 头文件
│   ├── jniLibs/arm64-v8a/           # 预编译的 MNN 库
│   │   └── libMNN.so                # MNN 核心库
│   └── java/.../core/prompt/mnn/   # Kotlin 封装层
│       ├── MnnBridge.kt             # 库加载和基础功能
│       ├── MnnLlmSession.kt         # LLM 会话管理
│       ├── MnnEmbeddingSession.kt   # Embedding 会话
│       ├── MnnChatClient.kt         # ChatClient 适配器
│       ├── MnnModelManager.kt       # 模型管理
│       ├── MnnModels.kt             # 数据模型
│       └── MnnConfig.kt             # 配置类
└── build.gradle.kts
```

## 核心功能

1. **LLM 推理**：通过 `MnnLlmSession` 进行本地模型推理
2. **Embedding**：通过 `MnnEmbeddingSession` 进行文本向量化（RAG）
3. **ChatClient 适配**：实现 `ChatClient` 接口，与框架无缝集成
4. **模型管理**：本地模型的加载、配置、删除等

## 使用方式

### 1. 添加依赖

```kotlin
dependencies {
    implementation(project(":jasmine-core:prompt:prompt-mnn"))
}
```

### 2. 创建 ChatClient

```kotlin
val mnnClient = MnnChatClient(
    context = context,
    modelId = "MNN_Qwen3.5-2B-MNN"
)

// 使用
val result = mnnClient.chat(messages, model = "MNN_Qwen3.5-2B-MNN")
```

### 3. 模型管理

```kotlin
// 获取本地模型列表
val models = MnnModelManager.getLocalModels(context)

// 删除模型
MnnModelManager.deleteModel(context, modelId)
```

## 迁移说明

此模块从应用层（`app/src/main/java/com/lhzkml/jasmine/mnn`）迁移到框架层。

**迁移内容**：
- ✅ JNI 层（C++ 代码）
- ✅ MNN 库文件（libMNN.so）
- ✅ 核心 Kotlin 类（MnnBridge, MnnLlmSession, MnnEmbeddingSession）
- ✅ ChatClient 实现
- ✅ 基础模型管理

**保留在应用层**：
- 模型下载（MnnDownloadManager）
- 模型导入导出
- UI 相关代码
- 应用特定配置

## 注意事项

1. 仅支持 ARM64 架构
2. 模型文件需要用户下载或导入
3. 推理性能受设备性能限制
