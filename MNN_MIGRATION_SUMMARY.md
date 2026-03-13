# MNN 本地模型迁移总结

## 迁移概述

已成功将 MNN 本地模型功能从应用层（`app` 模块）迁移到框架层（`jasmine-core/prompt/prompt-mnn` 模块）。

## 迁移内容

### 1. 新建模块

创建了 `jasmine-core/prompt/prompt-mnn` 模块，包含：

```
jasmine-core/prompt/prompt-mnn/
├── build.gradle.kts                 # 模块构建配置
├── README.md                        # 模块说明文档
└── src/main/
    ├── cpp/                         # C++ JNI 层
    │   ├── CMakeLists.txt          # CMake 配置
    │   ├── mnn_jni.cpp             # JNI 实现
    │   └── third_party/            # MNN 头文件
    ├── jniLibs/arm64-v8a/          # 预编译库
    │   └── libMNN.so               # MNN 核心库
    └── java/.../core/prompt/mnn/   # Kotlin 代码
        ├── MnnBridge.kt            # 库加载
        ├── MnnConfig.kt            # 配置类
        ├── MnnLlmSession.kt        # LLM 会话
        ├── MnnEmbeddingSession.kt  # Embedding 会话
        ├── MnnChatClient.kt        # ChatClient 实现
        ├── MnnModelManager.kt      # 模型管理（核心）
        └── MnnModels.kt            # 数据模型
```

### 2. 迁移的文件

#### C++ 层
- ✅ `mnn_jni.cpp` - JNI 桥接实现
- ✅ `CMakeLists.txt` - CMake 构建配置
- ✅ `third_party/` - MNN 头文件
- ✅ `libMNN.so` - MNN 预编译库

#### Kotlin 层
- ✅ `MnnBridge.kt` - 库加载和基础功能
- ✅ `MnnConfig.kt` - 配置数据类
- ✅ `MnnLlmSession.kt` - LLM 会话管理
- ✅ `MnnEmbeddingSession.kt` - Embedding 会话
- ✅ `MnnChatClient.kt` - ChatClient 适配器
- ✅ `MnnModelManager.kt` - 模型管理（核心功能）
- ✅ `MnnModels.kt` - 数据模型定义

### 3. 应用层改动

#### 更新的文件

**app/build.gradle.kts**
```kotlin
// 添加依赖
implementation(project(":jasmine-core:prompt:prompt-mnn"))
```

**app/src/main/java/com/lhzkml/jasmine/mnn/MnnChatClient.kt**
- 改为委托给框架层的 `CoreMnnChatClient`
- 保留应用层特定功能（Thinking 配置）

**app/src/main/java/com/lhzkml/jasmine/mnn/MnnModelManager.kt**
- 核心功能委托给框架层的 `CoreMnnModelManager`
- 保留应用层特定功能（下载、导入导出、市场数据）

#### 保留在应用层的功能

以下功能仍保留在 `app/src/main/java/com/lhzkml/jasmine/mnn/`：

- ✅ `MnnDownloadManager.kt` - 模型下载管理
- ✅ `MnnModelManager.kt` - 下载、导入导出、市场数据
- ✅ `MnnManagementActivity.kt` - 模型管理 UI
- ✅ `MnnModelMarketActivity.kt` - 模型市场 UI
- ✅ `MnnModelSettingsActivity.kt` - 模型设置 UI
- ✅ `MnnEmbeddingService.kt` - Embedding 服务

### 4. 配置更新

**settings.gradle.kts**
```kotlin
include(":jasmine-core:prompt:prompt-mnn")
```

## 架构优势

### 迁移前（应用层）
```
app/
└── src/main/java/com/lhzkml/jasmine/mnn/
    ├── MnnBridge.kt
    ├── MnnLlmSession.kt
    ├── MnnChatClient.kt
    ├── MnnModelManager.kt
    └── ... (所有功能都在应用层)
```

### 迁移后（分层架构）
```
jasmine-core/prompt/prompt-mnn/     # 框架层 - 核心功能
├── MnnBridge.kt                    # 库加载
├── MnnLlmSession.kt                # LLM 推理
├── MnnChatClient.kt                # ChatClient 实现
└── MnnModelManager.kt              # 模型管理（核心）

app/src/main/java/.../mnn/          # 应用层 - 扩展功能
├── MnnChatClient.kt                # 委托 + 应用特定功能
├── MnnModelManager.kt              # 委托 + 下载/导入导出
├── MnnDownloadManager.kt           # 下载管理
└── Mnn*Activity.kt                 # UI 界面
```

## 优势分析

### 1. 框架独立性
- ✅ 核心推理能力在框架层，可被其他应用复用
- ✅ 应用层只需添加依赖即可使用

### 2. 职责分离
- ✅ 框架层：核心推理、模型管理、ChatClient 适配
- ✅ 应用层：下载、导入导出、UI、应用特定配置

### 3. 可维护性
- ✅ 核心功能集中在框架层，易于维护和测试
- ✅ 应用层功能可独立演进

### 4. 可扩展性
- ✅ 其他应用可直接使用框架层的 MNN 能力
- ✅ 可轻松替换或添加其他推理引擎

## 使用示例

### 框架层使用（纯推理）

```kotlin
// 1. 创建 ChatClient
val mnnClient = MnnChatClient(
    context = context,
    modelId = "MNN_Qwen3.5-2B-MNN"
)

// 2. 推理
val result = mnnClient.chat(
    messages = messages,
    model = "MNN_Qwen3.5-2B-MNN"
)

// 3. 流式推理
mnnClient.chatStream(messages, model).collect { chunk ->
    print(chunk)
}
```

### 应用层使用（完整功能）

```kotlin
// 1. 下载模型
val downloadManager = MnnDownloadManager.getInstance(context)
downloadManager.startDownload(marketModel)

// 2. 导入模型
val modelId = MnnModelManager.importModelFromZip(context, zipUri)

// 3. 使用模型（与框架层相同）
val mnnClient = MnnChatClient(context, modelId)
val result = mnnClient.chat(messages, model)
```

## 兼容性

### 向后兼容
- ✅ 应用层 API 保持不变
- ✅ 现有代码无需修改
- ✅ 配置和数据格式不变

### 依赖关系
```
app
 └─> jasmine-core:prompt:prompt-mnn
      ├─> jasmine-core:prompt:prompt-llm
      └─> jasmine-core:prompt:prompt-model
```

## 测试建议

### 1. 框架层测试
- [ ] MnnBridge 库加载测试
- [ ] MnnLlmSession 推理测试
- [ ] MnnChatClient 接口测试
- [ ] MnnModelManager 模型管理测试

### 2. 应用层测试
- [ ] 模型下载功能测试
- [ ] 模型导入导出测试
- [ ] UI 界面测试
- [ ] 端到端推理测试

### 3. 集成测试
- [ ] 框架层与应用层集成测试
- [ ] ChatClient 在 Agent 模式下的测试
- [ ] RAG Embedding 功能测试

## 后续优化建议

### 1. 短期优化
- [ ] 添加单元测试
- [ ] 完善错误处理
- [ ] 优化内存管理

### 2. 中期优化
- [ ] 支持更多架构（x86_64, arm32）
- [ ] 添加模型量化支持
- [ ] 优化推理性能

### 3. 长期优化
- [ ] 支持其他推理引擎（ONNX Runtime, llama.cpp）
- [ ] 模型热更新
- [ ] 分布式推理

## 总结

MNN 本地模型已成功从应用层迁移到框架层，实现了：

1. ✅ 核心功能框架化，可被其他应用复用
2. ✅ 职责分离，框架层专注推理，应用层专注扩展
3. ✅ 向后兼容，现有代码无需修改
4. ✅ 架构清晰，易于维护和扩展

迁移后的架构更加合理，为未来的功能扩展和性能优化奠定了良好的基础。
