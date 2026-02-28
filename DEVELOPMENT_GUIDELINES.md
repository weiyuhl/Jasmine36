# Jasmine 开发注意事项

## 项目架构

本项目采用多模块架构，分为 **框架层 (jasmine-core)** 和 **应用层 (app)** 两部分。

```
Jasmine/
├── app/                          # 应用层 — Android UI、Activity、平台胶水代码
├── jasmine-core/                 # 框架层 — 纯业务逻辑，可独立测试
│   ├── prompt/
│   │   ├── prompt-model/         # 数据模型（ChatMessage, Prompt, ToolDescriptor 等）
│   │   ├── prompt-llm/           # LLM 客户端、Token 估算、上下文压缩
│   │   └── prompt-executor/      # ChatClient 工厂、路由
│   ├── agent/
│   │   ├── agent-tools/          # 工具注册、ToolExecutor（Agent Loop）
│   │   └── agent-runtime/        # Agent 运行时、图策略
│   ├── conversation/
│   │   └── conversation-storage/ # Room 数据库、DAO
│   └── config/
│       └── config-manager/       # ConfigRepository 接口、ProviderRegistry
```

## 核心原则

### 1. 框架与应用分离

- **所有业务逻辑、算法、数据处理必须写在 jasmine-core 中**
- **app 模块只做 UI 展示和平台适配**，不写业务逻辑
- app 通过接口（ConfigRepository、ChatClient 等）调用 core，不直接操作底层实现
- 判断标准：如果一段代码不依赖 Android Context / Activity / View，它就应该在 core 里

**错误示例：**
```kotlin
// ❌ 在 MainActivity 里写工具执行逻辑
class MainActivity {
    fun executeTools(messages: List<ChatMessage>) {
        // 不要在这里写业务逻辑
    }
}
```

**正确示例：**
```kotlin
// ✅ 业务逻辑在 core 模块
// jasmine-core/agent/agent-tools 中的 ToolExecutor
class ToolExecutor(private val client: ChatClient, ...) {
    suspend fun execute(prompt: Prompt, model: String): ChatResult { ... }
}

// app 层只负责调用和 UI 更新
class MainActivity {
    val result = executor.execute(prompt, model)
    tvOutput.text = result.content
}
```

### 2. 按功能拆分模块

- **不同功能必须放在独立的模块中**，不要把所有代码塞进同一个模块
- 新增功能时，先评估是否属于现有模块的职责范围；如果不属于，就新建模块
- 每个模块应该有单一、明确的职责

**模块划分原则：**
- 一个模块只做一件事（如 prompt-model 只定义数据模型，prompt-llm 只处理 LLM 通信）
- 如果一个模块的代码超出了它的命名范围，说明需要拆分
- 模块之间通过接口通信，降低耦合

**新建模块的时机：**
- 要开发的功能和现有模块没有直接关系
- 现有模块已经承担了太多职责
- 新功能有独立的依赖项，不想污染其他模块

**示例 — 如果要新增 RAG（检索增强生成）功能：**
```
jasmine-core/
├── rag/
│   └── rag-engine/       # ✅ 新建独立模块
│       ├── src/main/     # 向量检索、文档分块、嵌入等
│       └── src/test/     # 对应的单元测试
```

```kotlin
// ❌ 错误：把 RAG 逻辑塞进 prompt-llm
// prompt-llm 的职责是 LLM 通信，不应该包含检索逻辑

// ✅ 正确：新建 rag-engine 模块，prompt-llm 通过依赖调用
```

**新建模块步骤：**
1. 在 `jasmine-core/` 下按功能域创建目录（如 `jasmine-core/rag/rag-engine/`）
2. 添加 `build.gradle.kts`，声明依赖
3. 在 `settings.gradle.kts` 中 `include` 新模块
4. 在 `app/build.gradle.kts` 中添加对新模块的依赖
5. 编写代码和单元测试

### 3. 不写向后兼容代码

- **不保留旧方法签名**：重构后直接删除旧 API，更新所有调用点
- **不写 typealias 兼容别名**：直接使用正确的类型名
- **不写包装方法**：不为了兼容旧调用方式而保留多余的方法重载
- 如果 API 变更，全局搜索所有调用点一次性修改完毕

### 4. 不写数据库版本迁移

- **数据库 schema 变更时直接修改 Entity 类，版本号保持 1**
- 使用 `fallbackToDestructiveMigration(dropAllTables = true)` 处理版本不匹配
- 不写 `Migration(x, y)` 对象
- 本项目的本地数据（对话记录等）可以重建，不需要保留旧数据

### 5. 必须写单元测试

- **每个 core 模块的公开类都要有对应的测试**
- 测试文件放在对应模块的 `src/test/java/` 下
- 测试只测试当前 API，不为已删除的旧 API 写测试
- 使用 Fake/Stub 替代 Android 依赖，保证测试可以在 JVM 上运行

**测试命名规范：**
```kotlin
class TokenEstimatorTest {
    @Test
    fun `empty string is 0 tokens`() { ... }

    @Test
    fun `chinese text estimates higher than ascii`() { ... }
}
```

**构建前必须通过所有测试：**
```bash
.\gradlew.bat test
```

## 开发流程

1. 写代码 → 确保逻辑在正确的模块层级
2. 写/更新单元测试
3. 运行 `.\gradlew.bat test` 确认全部通过
4. 运行 `.\gradlew.bat assembleDebug` 构建 APK
5. 提交推送

## 模块依赖方向

```
app → jasmine-core (所有子模块)
agent-tools → prompt-model, prompt-llm
agent-runtime → agent-tools, prompt-model, prompt-llm
conversation-storage → prompt-model
prompt-llm → prompt-model
prompt-executor → prompt-llm, prompt-model
config-manager → prompt-model, prompt-executor
```

依赖只能从上往下，core 模块之间不能反向依赖 app。
