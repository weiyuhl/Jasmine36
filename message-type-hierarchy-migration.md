# Message 类型层次移植方案

> 将 jasmine 的 ChatMessage/ChatResult 扁平结构重构为 koog 的 Message 密封类层次
> 创建时间: 2026-02-21

---

## 一、架构对比

### koog 的 Message 密封类层次

```
Message (sealed interface)
  +-- Request (sealed interface) -- 发给 LLM 的消息
  |     +-- User (data class) -- 用户消息
  |     +-- System (data class) -- 系统消息
  |     +-- Tool.Result (data class) -- 工具执行结果
  +-- Response (sealed interface) -- LLM 返回的消息
  |     +-- Assistant (data class) -- 助手回复
  |     +-- Reasoning (data class) -- 推理/思考过程
  |     +-- Tool.Call (data class) -- 工具调用请求
  +-- Tool (sealed interface) -- 工具相关消息
        +-- Tool.Call (同时实现 Response)
        +-- Tool.Result (同时实现 Request)
```

koog 还有:
- `ContentPart` (sealed interface) -- 消息内容部分 (Text, Image, Video, Audio, File)
- `MessageMetaInfo` (sealed interface) -- 消息元数据
  - `RequestMetaInfo` -- 请求元数据 (timestamp)
  - `ResponseMetaInfo` -- 响应元数据 (timestamp + token counts)
- `Role` (enum) -- System, User, Assistant, Reasoning, Tool

### jasmine 当前的扁平结构

```
ChatMessage (data class)
  - role: String ("system" / "user" / "assistant" / "tool")
  - content: String
  - timestamp: Long?
  - finishReason: String?
  - metadata: JsonObject?
  - toolCalls: List<ToolCall>?
  - toolCallId: String?
  - toolName: String?

ChatResult (data class)
  - content: String
  - usage: Usage?
  - finishReason: String?
  - toolCalls: List<ToolCall>
  - thinking: String?
```

---

## 二、移植策略

采用渐进式重构，保持向后兼容:

1. 在 `prompt-model` 模块中创建新的 `Message` 密封类层次
2. 在 `ChatMessage` 上添加与 `Message` 的互转方法 (toMessage / fromMessage)
3. 逐步将内部模块从 `ChatMessage` 迁移到 `Message`
4. 最后移除 `ChatMessage` 的直接使用

**简化决策**:
- 不移植 `ContentPart` 的多媒体附件 (Image/Video/Audio/File)，只保留 Text
- 不移植 `kotlinx.datetime.Instant`，继续使用 `Long` 时间戳
- `ResponseMetaInfo` 简化为包含 `Usage` 的结构，复用现有 `Usage` 类
- 保留 `ChatResult` 作为 LLM 客户端返回类型，不改动 `ChatClient` 接口

---

## 三、影响范围分析

### 直接使用 ChatMessage 的文件 (26 个)

#### prompt-model 模块 (4 个)
1. `ChatMessage.kt` -- 核心定义，需要重构
2. `Prompt.kt` -- `messages: List<ChatMessage>`
3. `PromptBuilder.kt` -- 构建 ChatMessage 列表
4. `Tokenizer.kt` -- `tokenCountFor(message: ChatMessage)`

#### prompt-llm 模块 (7 个)
5. `ChatClient.kt` -- 接口参数 `messages: List<ChatMessage>`
6. `LLMSession.kt` -- 使用 ChatMessage 构建 prompt
7. `MultiChatClient.kt` -- 转发 ChatMessage
8. `ChatClientRouter.kt` -- 路由 ChatMessage
9. `ContextManager.kt` -- 管理 ChatMessage 上下文
10. `SystemPromptManager.kt` -- 系统消息管理
11. `StreamResumeHelper.kt` -- 流式恢复
12. `HistoryCompressionStrategy.kt` -- 历史压缩

#### prompt-executor 模块 (4 个)
13. `OpenAICompatibleClient.kt` -- ChatMessage -> API 请求
14. `ClaudeClient.kt` -- ChatMessage -> Claude API
15. `GeminiClient.kt` -- ChatMessage -> Gemini API
16. `VertexAIClient.kt` -- ChatMessage -> Vertex AI API

#### agent-tools 模块 (7 个)
17. `ToolExecutor.kt` -- 工具执行循环
18. `PredefinedNodes.kt` -- 预定义节点
19. `PredefinedStrategies.kt` -- 预定义策略
20. `FunctionalContextExt.kt` -- 函数式上下文扩展
21. `AgentEdge.kt` -- 边条件 (使用 ChatResult)
22. `SimpleLLMPlanner.kt` -- 规划器
23. `SimpleLLMWithCriticPlanner.kt` -- 带评审的规划器

#### snapshot 模块 (2 个)
24. `Persistence.kt` -- 检查点持久化
25. `AgentCheckpoint.kt` -- 检查点数据

#### conversation-storage 模块 (1 个)
26. `ConversationRepository.kt` -- 对话存储

#### app 层 (2 个)
27. `MainActivity.kt` -- 主界面
28. `CheckpointDetailActivity.kt` -- 检查点详情

### 直接使用 ChatResult 的文件 (15 个)

ChatResult 主要在 LLM 请求返回值和边条件中使用，与 ChatMessage 有部分重叠。

### 测试文件 (6 个)
29. `ChatMessageTest.kt`
30. `ChatResultTest.kt`
31. `LLMSessionTest.kt`
32. `MultiChatClientTest.kt`
33. `ChatClientRouterTest.kt`
34. `HistoryCompressionStrategyTest.kt`

---

## 四、分步实施计划

### 步骤 1: 创建 Message 密封类层次 (新增文件，不改动现有代码)

在 `jasmine-core/prompt/prompt-model/src/main/java/com/lhzkml/jasmine/core/prompt/model/` 下创建:

**1.1 创建 `MessageRole.kt`**
```kotlin
enum class MessageRole {
    System, User, Assistant, Reasoning, Tool
}
```

**1.2 创建 `MessageMetaInfo.kt`**
```kotlin
sealed interface MessageMetaInfo {
    val timestamp: Long?
    val metadata: JsonObject?
}

data class RequestMetaInfo(
    override val timestamp: Long? = null,
    override val metadata: JsonObject? = null
) : MessageMetaInfo {
    companion object {
        val Empty = RequestMetaInfo()
    }
}

data class ResponseMetaInfo(
    override val timestamp: Long? = null,
    override val metadata: JsonObject? = null,
    val usage: Usage? = null,
    val finishReason: String? = null
) : MessageMetaInfo {
    companion object {
        val Empty = ResponseMetaInfo()
    }
}
```

**1.3 创建 `Message.kt`**
```kotlin
@Serializable
sealed interface Message {
    val content: String
    val role: MessageRole
    val metaInfo: MessageMetaInfo

    // Request -- 发给 LLM 的消息
    sealed interface Request : Message {
        override val metaInfo: RequestMetaInfo
    }

    // Response -- LLM 返回的消息
    sealed interface Response : Message {
        override val metaInfo: ResponseMetaInfo
    }

    // 用户消息
    data class User(
        override val content: String,
        override val metaInfo: RequestMetaInfo = RequestMetaInfo.Empty
    ) : Request {
        override val role: MessageRole = MessageRole.User
    }

    // 系统消息
    data class System(
        override val content: String,
        override val metaInfo: RequestMetaInfo = RequestMetaInfo.Empty
    ) : Request {
        override val role: MessageRole = MessageRole.System
    }

    // 助手消息
    data class Assistant(
        override val content: String,
        override val metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty,
        val toolCalls: List<ToolCall> = emptyList()
    ) : Response {
        override val role: MessageRole = MessageRole.Assistant
        val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
    }

    // 推理/思考消息
    data class Reasoning(
        override val content: String,
        override val metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty,
        val id: String? = null,
        val encrypted: String? = null
    ) : Response {
        override val role: MessageRole = MessageRole.Reasoning
    }

    // 工具相关消息
    sealed interface Tool : Message {
        val toolId: String?
        val toolName: String

        // 工具调用 (LLM 发出)
        data class Call(
            override val toolId: String?,
            override val toolName: String,
            override val content: String,  // 参数 JSON
            override val metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty
        ) : Tool, Response {
            override val role: MessageRole = MessageRole.Tool
        }

        // 工具结果 (回传给 LLM)
        data class Result(
            override val toolId: String?,
            override val toolName: String,
            override val content: String,  // 执行结果
            override val metaInfo: RequestMetaInfo = RequestMetaInfo.Empty
        ) : Tool, Request {
            override val role: MessageRole = MessageRole.Tool
        }
    }
}

typealias LLMChoice = List<Message.Response>
```

**验证**: 构建通过，不影响现有代码。

---

### 步骤 2: 添加 ChatMessage <-> Message 互转方法

在 `ChatMessage.kt` 中添加:

**2.1 ChatMessage.toMessage() 方法**
```kotlin
fun ChatMessage.toMessage(): Message {
    return when (role) {
        "system" -> Message.System(content, RequestMetaInfo(timestamp))
        "user" -> Message.User(content, RequestMetaInfo(timestamp))
        "assistant" -> {
            if (!toolCalls.isNullOrEmpty()) {
                // 带工具调用的助手消息 -> 拆分为 Assistant + Tool.Call 列表
                Message.Assistant(content, ResponseMetaInfo(timestamp, finishReason = finishReason), toolCalls)
            } else {
                Message.Assistant(content, ResponseMetaInfo(timestamp, finishReason = finishReason))
            }
        }
        "tool" -> Message.Tool.Result(toolCallId, toolName ?: "", content, RequestMetaInfo(timestamp))
        else -> Message.User(content, RequestMetaInfo(timestamp))  // fallback
    }
}
```

**2.2 Message.toChatMessage() 扩展方法**
```kotlin
fun Message.toChatMessage(): ChatMessage {
    return when (this) {
        is Message.System -> ChatMessage.system(content)
        is Message.User -> ChatMessage.user(content)
        is Message.Assistant -> {
            if (hasToolCalls) ChatMessage.assistantWithToolCalls(toolCalls, content)
            else ChatMessage.assistant(content, finishReason = metaInfo.finishReason, timestamp = metaInfo.timestamp)
        }
        is Message.Reasoning -> ChatMessage("reasoning", content, timestamp = metaInfo.timestamp)
        is Message.Tool.Call -> ChatMessage("assistant", content, toolCalls = listOf(ToolCall(toolId ?: "", toolName, content)))
        is Message.Tool.Result -> ChatMessage.toolResult(ToolResult(toolId ?: "", toolName, content))
    }
}
```

**2.3 ChatResult.toAssistantMessage() 方法**
```kotlin
fun ChatResult.toAssistantMessage(): Message.Response {
    return if (hasToolCalls) {
        Message.Assistant(content, ResponseMetaInfo(usage = usage, finishReason = finishReason), toolCalls)
    } else if (thinking != null) {
        Message.Reasoning(thinking)
    } else {
        Message.Assistant(content, ResponseMetaInfo(usage = usage, finishReason = finishReason))
    }
}
```

**2.4 List<ChatMessage>.toMessages() / List<Message>.toChatMessages() 批量转换**

**验证**: 构建通过，所有现有测试通过。

---

### 步骤 3: 迁移 Prompt 和 PromptBuilder

**3.1 Prompt 添加 Message 版本的属性和方法**

在 `Prompt` 中添加:
```kotlin
// 新增: Message 类型的消息列表（惰性转换）
val typedMessages: List<Message> get() = messages.map { it.toMessage() }

// 新增: 从 Message 列表构建 Prompt
companion object {
    fun fromMessages(messages: List<Message>, id: String, ...): Prompt =
        Prompt(messages.map { it.toChatMessage() }, id, ...)
}
```

**3.2 PromptBuilder 添加 Message 版本的方法**

在 `PromptBuilder` 中添加:
```kotlin
fun message(msg: Message) {
    messages.add(msg.toChatMessage())
}

fun reasoning(content: String) {
    messages.add(ChatMessage("reasoning", content))
}
```

**验证**: 构建通过，所有现有测试通过。

---

### 步骤 4: 迁移 LLMSession

**4.1 在 LLMSession 中添加 Message 类型的请求方法**

```kotlin
// 新增: 返回 Message.Response 的请求方法
suspend fun requestLLMAsMessage(): Message.Response {
    return requestLLM().toAssistantMessage()
}
```

**4.2 在 LLMWriteSession 中添加 Message 版本的 appendPrompt**

```kotlin
fun appendMessage(message: Message) {
    appendPrompt { message(message.toChatMessage()) }
}
```

**验证**: 构建通过，所有现有测试通过。

---

### 步骤 5: 迁移 agent-tools 模块

**5.1 PredefinedNodes -- 添加 Message 类型的节点变体**

为关键节点添加 Message 版本:
```kotlin
// 现有: nodeLLMRequest 返回 ChatResult
// 新增: nodeLLMRequestAsMessage 返回 Message.Response
```

**5.2 AgentEdge -- 添加 Message.Response 版本的边条件**

```kotlin
// 新增: 基于 Message.Assistant 的边条件
infix fun <TFrom, TTo> EdgeBuilder<TFrom, Message.Response, TTo>.onAssistant(...)
infix fun <TFrom, TTo> EdgeBuilder<TFrom, Message.Response, TTo>.onToolCall(...)
infix fun <TFrom, TTo> EdgeBuilder<TFrom, Message.Response, TTo>.onReasoning(...)
```

**5.3 FunctionalContextExt -- 添加 Message 版本的扩展函数**

**验证**: 构建通过，所有现有测试通过。

---

### 步骤 6: 迁移 snapshot/Persistence

**6.1 AgentCheckpoint -- 添加 Message 版本的消息历史**

```kotlin
data class AgentCheckpoint(
    ...
    val messageHistory: List<ChatMessage>,  // 保留现有
    // 新增: 类型化消息历史（惰性转换）
    val typedMessageHistory: List<Message> get() = messageHistory.map { it.toMessage() }
)
```

**6.2 Persistence -- 内部使用 Message 类型**

**验证**: 构建通过，所有现有测试通过。

---

### 步骤 7: 迁移 conversation-storage

**7.1 ConversationRepository -- 添加 Message 版本的方法**

```kotlin
suspend fun addTypedMessage(conversationId: String, message: Message) {
    addMessage(conversationId, message.toChatMessage())
}

suspend fun getTypedMessages(conversationId: String): List<Message> {
    return getMessages(conversationId).map { it.toMessage() }
}
```

**验证**: 构建通过，所有现有测试通过。

---

### 步骤 8: 创建测试

**8.1 MessageTest.kt -- Message 密封类层次测试**
- 各子类创建和属性验证
- role 正确性
- Request/Response 类型判断

**8.2 MessageConversionTest.kt -- 互转测试**
- ChatMessage -> Message -> ChatMessage 往返一致性
- ChatResult -> Message.Response 转换
- 各种角色的转换覆盖

**验证**: 所有测试通过。

---

### 步骤 9: (可选) 逐步替换内部使用

在后续迭代中，可以逐步将内部模块从 ChatMessage 切换到 Message:

1. `Prompt.messages` 类型从 `List<ChatMessage>` 改为 `List<Message>`
2. `ChatClient` 接口参数从 `List<ChatMessage>` 改为 `List<Message>`
3. `LLMSession` 内部全面使用 `Message`
4. `PredefinedNodes` / `PredefinedStrategies` 全面使用 `Message`
5. 最终废弃 `ChatMessage`

这些步骤影响面大，建议在所有新代码都使用 Message 后再执行。

---

## 五、风险评估

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 互转丢失信息 | ChatMessage 的某些字段在 Message 中没有对应 | 确保 toMessage/toChatMessage 覆盖所有字段 |
| 序列化兼容性 | Message 的序列化格式与 ChatMessage 不同 | 保留 ChatMessage 的序列化，Message 用于内部逻辑 |
| 性能开销 | 频繁的 toMessage/toChatMessage 转换 | 惰性转换 + 缓存 |
| app 层兼容 | MainActivity 等直接使用 ChatMessage | 步骤 1-8 不改动 app 层代码 |

---

## 六、不移植的 koog 特性

| 特性 | 原因 |
|---|---|
| ContentPart.Attachment (Image/Video/Audio/File) | jasmine 无多媒体消息需求 |
| kotlinx.datetime.Instant | jasmine 使用 Long 时间戳 |
| @JvmOverloads 构造函数 | Android 项目不需要 Java 互操作 |
| ResponseMetaInfo.additionalInfo (deprecated) | 已废弃字段 |
| Message.hasAttachments() / hasOnlyTextContent() | 依赖 ContentPart.Attachment |

---

## 七、执行顺序

建议按步骤 1 -> 2 -> 8 -> 3 -> 4 -> 5 -> 6 -> 7 的顺序执行。

先创建核心类型和互转方法，然后写测试验证，最后逐步迁移各模块。每个步骤完成后构建+测试+提交。
