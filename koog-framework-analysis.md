# Koog 0.6.2 框架分析文档

## 概述

Koog 是 JetBrains 开发的基于 Kotlin 的 AI Agent 框架，处于 Alpha 阶段（孵化项目）。它用于构建和运行 AI 智能体，支持工具调用、复杂工作流、多轮对话、流式响应等能力。

框架基于 Kotlin Multiplatform，支持 JVM、JS、WasmJS、Android、iOS 等目标平台。

## 项目结构

整个框架采用高度模块化设计，主要分为以下几大模块：

### 1. agents — 智能体核心

智能体的核心实现，是整个框架的上层入口。

| 子模块 | 说明 |
|--------|------|
| agents-core | Agent 核心逻辑：AIAgent 接口、策略（functional/graph）、上下文、状态管理 |
| agents-tools | 工具系统：Tool 基类、ToolDescriptor、ToolRegistry、工具序列化 |
| agents-ext | Agent 扩展功能 |
| agents-mcp | MCP（Model Context Protocol）工具集成 |
| agents-mcp-server | MCP 服务端实现 |
| agents-planner | Agent 规划器 |
| agents-test | Agent 测试工具 |
| agents-utils | Agent 工具类 |

#### agents-features — 可插拔特性模块

| 特性模块 | 说明 |
|----------|------|
| agents-features-a2a-client | A2A（Agent-to-Agent）客户端 |
| agents-features-a2a-core | A2A 核心协议 |
| agents-features-a2a-server | A2A 服务端 |
| agents-features-acp | ACP（Agent Client Protocol）支持 |
| agents-features-event-handler | 事件处理器 |
| agents-features-memory | Agent 记忆/知识保持 |
| agents-features-opentelemetry | OpenTelemetry 可观测性集成 |
| agents-features-snapshot | Agent 状态快照/持久化 |
| agents-features-sql | SQL 相关功能 |
| agents-features-tokenizer | Token 计数器 |
| agents-features-trace | 执行追踪 |

### 2. prompt — 提示词系统

处理与 LLM 交互的提示词构建、消息模型、参数配置等。

| 子模块 | 说明 |
|--------|------|
| prompt-model | 核心数据模型：Message、Prompt、LLMParams、StreamFrame、ContentPart |
| prompt-llm | LLM 模型定义：LLModel、LLMProvider、LLMCapability |
| prompt-executor | 提示词执行器（见下方详细说明） |
| prompt-cache | 提示词缓存（文件缓存、Redis 缓存） |
| prompt-markdown | Markdown 处理 |
| prompt-xml | XML 处理 |
| prompt-structure | 结构化输出（JSON Schema） |
| prompt-processor | 响应处理器 |
| prompt-tokenizer | Token 分词器 |

#### prompt-executor — 执行器与客户端

这是连接 Agent 和 LLM API 的桥梁层。

| 子模块 | 说明 |
|--------|------|
| prompt-executor-model | PromptExecutor 接口定义 |
| prompt-executor-llms | SingleLLMPromptExecutor 实现 |
| prompt-executor-llms-all | 聚合所有 LLM 客户端的执行器 |
| prompt-executor-cached | 带缓存的执行器 |

#### prompt-executor-clients — LLM 供应商客户端

| 客户端 | 说明 |
|--------|------|
| prompt-executor-openai-client-base | OpenAI 兼容协议基类（AbstractOpenAILLMClient） |
| prompt-executor-openai-client | OpenAI 官方客户端 |
| prompt-executor-deepseek-client | DeepSeek 客户端 |
| prompt-executor-anthropic-client | Anthropic Claude 客户端 |
| prompt-executor-google-client | Google Gemini 客户端 |
| prompt-executor-openrouter-client | OpenRouter 客户端 |
| prompt-executor-ollama-client | Ollama 本地模型客户端 |
| prompt-executor-bedrock-client | AWS Bedrock 客户端 |
| prompt-executor-dashscope-client | 阿里云 DashScope 客户端 |
| prompt-executor-mistralai-client | Mistral AI 客户端 |

### 3. http-client — HTTP 客户端抽象层

对 HTTP 请求的统一抽象，支持多种底层实现。

| 子模块 | 说明 |
|--------|------|
| http-client-core | HTTP 客户端核心接口（KoogHttpClient） |
| http-client-ktor | 基于 Ktor 的实现 |
| http-client-okhttp | 基于 OkHttp 的实现 |
| http-client-java | 基于 Java HttpClient 的实现 |
| http-client-test | HTTP 客户端测试工具 |

### 4. a2a — Agent-to-Agent 协议

实现 Agent 之间的通信协议。

| 子模块 | 说明 |
|--------|------|
| a2a-core | A2A 核心协议定义 |
| a2a-client | A2A 客户端 |
| a2a-server | A2A 服务端 |
| a2a-transport | 传输层（基于 JSON-RPC over HTTP） |
| a2a-test | A2A 测试工具 |

### 5. embeddings — 向量嵌入

| 子模块 | 说明 |
|--------|------|
| embeddings-base | 嵌入基础接口（Embedder、Vector） |
| embeddings-llm | 基于 LLM 的嵌入实现（支持 Ollama） |

### 6. rag — 检索增强生成

| 子模块 | 说明 |
|--------|------|
| rag-base | RAG 基础实现 |
| vector-storage | 向量存储 |

### 7. 集成模块

| 模块 | 说明 |
|------|------|
| koog-agents | 聚合依赖包（一个依赖引入所有核心模块） |
| koog-ktor | Ktor 框架集成 |
| koog-spring-boot-starter | Spring Boot Starter 集成 |

### 8. 其他

| 模块 | 说明 |
|------|------|
| utils | 工具类（跨平台支持） |
| test-utils | 测试工具 |
| inspections | 代码检查 |
| convention-plugin-ai | Gradle 构建约定插件 |
| examples | 示例项目 |
| docs | 文档 |

## 核心架构设计

### 分层架构

```
┌─────────────────────────────────────────┐
│              应用层 (App)                │
├─────────────────────────────────────────┤
│         Agent 层 (agents-core)          │
│   AIAgent / Strategy / Context / State  │
├─────────────────────────────────────────┤
│         工具层 (agents-tools)           │
│   Tool / ToolDescriptor / ToolRegistry  │
├─────────────────────────────────────────┤
│       执行器层 (prompt-executor)         │
│   PromptExecutor / SingleLLMExecutor    │
├─────────────────────────────────────────┤
│       客户端层 (executor-clients)        │
│   LLMClient / OpenAI / DeepSeek / ...  │
├─────────────────────────────────────────┤
│       提示词层 (prompt-model)           │
│   Prompt / Message / LLMParams         │
├─────────────────────────────────────────┤
│       模型定义层 (prompt-llm)           │
│   LLModel / LLMProvider / LLMCapability│
├─────────────────────────────────────────┤
│       HTTP 层 (http-client)            │
│   KoogHttpClient / Ktor / OkHttp       │
└─────────────────────────────────────────┘
```

### 核心接口说明

#### LLMClient — LLM 客户端接口

所有供应商客户端的基础接口，定义了与 LLM 通信的标准方法：

- `execute(prompt, model, tools)` — 执行提示词，返回响应消息列表
- `executeStreaming(prompt, model, tools)` — 流式执行，返回 `Flow<StreamFrame>`
- `executeMultipleChoices(prompt, model, tools)` — 多选项生成
- `moderate(prompt, model)` — 内容审核
- `models()` — 获取可用模型列表
- `llmProvider()` — 获取供应商标识
- `close()` — 关闭客户端释放资源

#### PromptExecutor — 提示词执行器

连接 Agent 和 LLMClient 的中间层，一个执行器可以管理多个 LLMClient：

- `execute(prompt, model, tools)` — 执行提示词
- `executeStreaming(prompt, model, tools)` — 流式执行
- `moderate(prompt, model)` — 内容审核

#### AIAgent — 智能体接口

顶层智能体接口，定义了 Agent 的生命周期：

- `run(agentInput)` — 执行 Agent
- `getState()` — 获取当前状态（NotStarted / Starting / Running / Finished / Failed）
- `result()` — 获取执行结果

#### LLModel — 模型定义

描述一个 LLM 模型的完整信息：

- `provider` — 供应商（LLMProvider）
- `id` — 模型标识（如 "gpt-4o"、"deepseek-chat"）
- `capabilities` — 能力列表（LLMCapability）
- `contextLength` — 上下文长度
- `maxOutputTokens` — 最大输出 token 数

#### LLMCapability — 模型能力

用 sealed class 定义模型支持的能力：

| 能力 | 说明 |
|------|------|
| Completion | 文本补全/生成 |
| Temperature | 温度参数控制 |
| Tools | 工具调用 |
| ToolChoice | 工具选择策略 |
| MultipleChoices | 多选项生成 |
| Vision.Image | 图片理解 |
| Vision.Video | 视频理解 |
| Audio | 音频处理 |
| Document | 文档处理 |
| Embed | 向量嵌入 |
| PromptCaching | 提示词缓存 |
| Moderation | 内容审核 |
| Schema.JSON.Basic | 基础 JSON 结构化输出 |
| Schema.JSON.Standard | 标准 JSON Schema 输出 |
| OpenAIEndpoint.Completions | OpenAI Chat Completions 端点 |
| OpenAIEndpoint.Responses | OpenAI Responses 端点 |

#### Message — 消息模型

消息类型采用 sealed interface 设计：

| 类型 | 角色 | 说明 |
|------|------|------|
| Message.System | system | 系统提示 |
| Message.User | user | 用户消息（支持文本、图片、音频、文件等多模态） |
| Message.Assistant | assistant | 助手回复 |
| Message.Reasoning | reasoning | 推理过程（如 DeepSeek 的思考链） |
| Message.Tool.Call | tool | 工具调用请求 |
| Message.Tool.Result | tool | 工具调用结果 |

每条消息包含 `parts`（内容部分列表）和 `metaInfo`（元信息，如 token 数、时间戳）。

### Agent 策略模式

Koog 提供两种 Agent 执行策略：

#### 1. Functional Strategy（函数式策略）

简单的循环式执行，适合单一任务：

```kotlin
val strategy = functionalStrategy<String, String>("chat") { input ->
    val response = llm.writeSession {
        appendPrompt { user(input) }
        requestLLMWithoutTools()
    }
    response.content
}
```

#### 2. Graph Strategy（图策略）

基于有向图的复杂工作流，支持节点、边、子图，适合多步骤任务。

### 工具系统

工具是 Agent 与外部世界交互的方式：

- `Tool<TArgs, TResult>` — 工具基类，泛型定义输入输出
- `ToolDescriptor` — 工具描述（名称、说明、参数 schema），供 LLM 理解工具用途
- `ToolRegistry` — 工具注册表，管理可用工具
- 支持通过 `@LLMDescription` 注解自动生成工具描述
- 支持 MCP 协议的外部工具集成

### OpenAI 兼容客户端架构

`AbstractOpenAILLMClient` 是所有 OpenAI 兼容供应商的基类，提供：

- HTTP 请求/响应处理
- Prompt 到 OpenAI Message 格式的转换
- 工具描述到 OpenAI Tool 格式的转换
- 流式响应（SSE）处理
- 多模态内容（图片、音频、文件）处理

具体供应商（DeepSeek、OpenRouter 等）继承此基类，只需实现：
- `serializeProviderChatRequest()` — 序列化请求
- `processProviderChatResponse()` — 处理响应
- `decodeResponse()` / `decodeStreamingResponse()` — 解码响应

## 支持的 LLM 供应商

| 供应商 | LLMProvider 标识 | 客户端类 |
|--------|-----------------|---------|
| OpenAI | OpenAI | OpenAILLMClient |
| Google Gemini | Google | GoogleLLMClient |
| Anthropic Claude | Anthropic | AnthropicLLMClient |
| DeepSeek | DeepSeek | DeepSeekLLMClient |
| OpenRouter | OpenRouter | OpenRouterLLMClient |
| Ollama | Ollama | OllamaLLMClient |
| AWS Bedrock | Bedrock | BedrockLLMClient |
| 阿里云 DashScope | Alibaba | DashScopeLLMClient |
| Mistral AI | MistralAI | MistralAILLMClient |

## 技术依赖

- Kotlin 2.2.10
- kotlinx-coroutines 1.10.2
- kotlinx-serialization 1.8.1
- Ktor（HTTP 客户端 + SSE）
- kotlinx-datetime
- kotlin-logging（日志）
- JDK 17+

## 对 Jasmine 项目的参考价值

Koog 的分层设计值得借鉴，特别是：

1. **客户端抽象** — `LLMClient` 接口 + `AbstractOpenAILLMClient` 基类的模式，可以让不同供应商共享 OpenAI 兼容协议的实现
2. **模型能力系统** — `LLMCapability` 的 sealed class 设计，可以在编译期检查模型是否支持某个功能
3. **消息模型** — `Message` 的 sealed interface 设计，类型安全地表示不同角色的消息
4. **执行器模式** — `PromptExecutor` 作为中间层，解耦 Agent 逻辑和具体的 LLM 调用
5. **工具系统** — 如果后续需要让 AI 调用外部工具，可以参考 Koog 的 Tool/ToolDescriptor 设计
