# MNN LLM 集成使用指南

## 概述

已完成 MNN LLM 在 Jasmine 应用层的集成，参考官方示例实现了完整的流式文本生成功能。

## 核心实现

### 1. JNI 层 (`app/src/main/cpp/mnn_jni.cpp`)

参考官方 `llm_session.cpp` 实现：

- **UTF-8 流处理器**：处理流式输出的 UTF-8 字符
- **流缓冲区**：用于 `std::ostream` 输出
- **LLM 初始化**：使用 `Llm::createLLM()` 和 `llm->load()`
- **流式生成**：使用 `llm->response()` + `llm->generate()` 循环

### 2. Kotlin 封装 (`MnnLlmSession.kt`)

```kotlin
// 创建会话
val session = MnnLlmSession(
    modelPath = "/path/to/model",
    config = MnnConfig(
        maxNewTokens = 2048,
        temperature = 0.7f,
        systemPrompt = "You are a helpful assistant."
    )
)

// 初始化
if (session.init()) {
    // 流式生成
    session.generate("你好") { token ->
        print(token)  // 实时输出每个 token
        false  // 返回 true 可以停止生成
    }
    
    // 简单生成
    val response = session.generate("你好")
    println(response)
    
    // 释放资源
    session.release()
}
```

## 关键特性

1. **流式输出**：实时返回生成的 token
2. **UTF-8 处理**：正确处理中文等多字节字符
3. **可中断**：通过回调返回值控制生成停止
4. **配置灵活**：支持温度、top-p、top-k 等参数

## 文件结构

```
app/
├── src/main/
│   ├── cpp/
│   │   ├── mnn_jni.cpp          # JNI 实现
│   │   └── CMakeLists.txt       # CMake 配置
│   ├── jniLibs/
│   │   └── arm64-v8a/
│   │       └── libMNN.so        # MNN 库
│   └── java/com/lhzkml/jasmine/mnn/
│       ├── MnnBridge.kt         # 基础桥接
│       └── MnnLlmSession.kt     # LLM 会话封装
```

## 下一步

1. 准备模型文件（MNN 格式）
2. 在 `TestMnnActivity` 中测试实际推理
3. 集成到 Jasmine 的对话流程中
