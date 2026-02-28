# langchain4j vs jasmine-core 完整对比分析

## 1. 整体架构对比

| 维度 | langchain4j | jasmine-core |
|------|-------------|-------------|
| 语言 | Java | Kotlin |
| 模块数 | ~75 个 Maven 模块 | 6 个 Gradle 模块 |
| 定位 | 通用 LLM 框架（服务端） | 移动端 Agent 框架（Android） |
| 设计哲学 | SPI 插件化 + 注解驱动 | 轻量级 + 手动组装 |

## 2. 模块映射

### 2.1 模型抽象层

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| `langchain4j-core` (ChatModel/StreamingChatModel/ChatRequest/ChatResponse) | `prompt-model` (ChatMessage/ChatRequest/ChatResponse/ChatResult) + `prompt-llm` (ChatClient/LLMSession) | Jasmine 拆成两层：纯数据模型 + LLM 会话管理 |
| ChatRequestParameters (temperature/topP/topK/maxTokens/toolSpecifications 统一参数) | SamplingParams + Prompt.maxTokens 分散管理 | langchain4j 参数更统一 |
| ChatModel.chat(ChatRequest) -> ChatResponse | ChatClient.chat()/chatStream() | 功能等价 |
| StreamingChatModel + TokenStream | ChatClient.chatStream() + StreamResult | Jasmine 用 suspend + callback，langchain4j 用 Flux/TokenStream |
| ChatModelListener (请求/响应监听) | 无对应 | Jasmine 缺少模型层监听器 |
| Capability (模型能力声明) | 无对应 | Jasmine 缺少模型能力声明机制 |

### 2.2 供应商适配

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| langchain4j-open-ai (独立模块) | OpenAIClient / GenericOpenAIClient | Jasmine 内置在 prompt-executor |
| langchain4j-anthropic (独立模块) | ClaudeClient / GenericClaudeClient | 同上 |
| langchain4j-google-ai-gemini (独立模块) | GeminiClient / GenericGeminiClient / VertexAIClient | 同上 |
| 20+ 供应商模块 (Ollama/Mistral/Qwen/Zhipu 等) | DeepSeekClient / SiliconFlowClient + OpenAICompatibleClient 兼容层 | Jasmine 用 OpenAI 兼容协议覆盖大部分供应商 |
| ChatClientFactory (SPI 自动发现) | ChatClientFactory (手动 when 分支) | langchain4j 用 SPI，Jasmine 用硬编码路由 |

### 2.3 工具系统

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| ToolSpecification (name/description/parameters/metadata) | ToolDescriptor (name/description/parameters) | 基本等价，langchain4j 多了 metadata |
| @Tool 注解 + ToolSpecifications 自动生成 | Tool 接口手动实现 | langchain4j 支持注解驱动，Jasmine 纯手动 |
| ToolProvider / ToolProviderRequest / ToolProviderResult | ToolRegistry.build {} | langchain4j 有动态工具提供机制，Jasmine 是静态注册 |
| ToolExecutor (执行单个工具) | ToolRegistry.execute(call) | 功能等价 |
| Guardrails (InputGuardrail/OutputGuardrail) | 无对应 | Jasmine 缺少输入/输出安全护栏 |

### 2.4 Agent 执行

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| AiServices (声明式 Agent，注解 + 代理) | ToolExecutor (命令式 Agent Loop) | 设计理念不同：langchain4j 声明式，Jasmine 命令式 |
| @Agent 注解 (name/description/async/outputKey) | 无对应 | Jasmine 缺少声明式 Agent |
| AgentBuilder / UntypedAgentBuilder | ToolExecutor 构造函数 | langchain4j 更灵活的构建器 |
| AgenticServices (多 Agent 编排入口) | 无对应 | Jasmine 缺少多 Agent 编排入口 |

### 2.5 工作流编排

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| LoopAgent / LoopAgentService | ToolExecutor (while 循环) | 功能等价 |
| SequenceAgent / SequentialAgentService | 无对应 | Jasmine 缺少顺序编排 |
| ParallelAgent / ParallelAgentService | 无对应 | Jasmine 缺少并行编排 |
| ConditionalAgent / ConditionalAgentService | 无对应 | Jasmine 缺少条件编排 |
| SupervisorAgent / SupervisorPlanner | 无对应 | Jasmine 缺少监督者模式 |
| HumanInTheLoop | ExecuteShellCommandTool 的确认机制（部分） | langchain4j 是通用的人机交互，Jasmine 仅限 Shell 确认 |

### 2.6 图执行引擎

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| 无独立图引擎（通过 workflow 组合） | GraphAgent + AgentNode/AgentEdge/AgentSubgraph/AgentStrategy | Jasmine 有独立的图执行引擎，langchain4j 没有 |
| 无 | PredefinedStrategies (singleRun/multiStep 等) | Jasmine 独有 |
| 无 | GraphStrategyBuilder DSL | Jasmine 独有 |

### 2.7 规划器

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| Planner 接口 + PlannerBasedService | AgentPlanner 接口 | 抽象层等价 |
| PlannerAgent (声明式) | SimpleLLMPlanner / SimpleLLMWithCriticPlanner | Jasmine 有 Critic 评估，langchain4j 没有 |
| 无 | GOAPPlanner (目标导向行动规划) | Jasmine 独有 |
| SupervisorPlanner | 无对应 | langchain4j 独有 |

### 2.8 MCP 协议

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| McpClient 接口 (4 种传输) | McpClient 接口 (2 种传输) | langchain4j 多 Stdio 和 WebSocket |
| HttpMcpClient (Streamable HTTP) | HttpMcpClient | 等价 |
| SseMcpClient (SSE) | SseMcpClient | 等价 |
| StdioMcpClient (标准输入输出) | 无对应 | 移动端不需要 Stdio |
| WebSocketMcpClient | 无对应 | Jasmine 缺少 WebSocket 传输 |
| McpToolProvider (动态工具提供 + 过滤 + 映射) | McpToolRegistryProvider + McpToolAdapter | langchain4j 的过滤/映射更强大 |
| ResourcesAsTools (MCP 资源转工具) | 无对应 | Jasmine 缺少资源转工具 |
| MCP Registry Client | 无对应 | Jasmine 缺少注册中心客户端 |

### 2.9 可观测性

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| AgentListener (onRequest/onResponse/onError/beforeToolExecution/afterToolExecution) | AgentEventListener (onToolCallStart/onToolCallResult/onThinking/onCompression/onCompletion) | 功能类似，Jasmine 更面向 UI |
| AgentMonitor / MonitoredExecution | 无对应 | langchain4j 有执行监控包装器 |
| HtmlReportGenerator | 无对应 | langchain4j 可生成 HTML 报告 |
| ChatModelListener (模型层监听) | 无对应 | Jasmine 缺少模型层监听 |
| Tracing (无独立模块) | Tracing + TraceEvent + TraceWriter (Log/File/Callback) | Jasmine 的追踪系统更完整 |
| 无 | EventHandler (分类事件系统: AGENT/TOOL/LLM/STRATEGY/NODE/SUBGRAPH/STREAMING) | Jasmine 独有的分类事件系统 |

### 2.10 持久化/快照

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| AgenticScope / AgenticScopePersister / AgenticScopeStore | Persistence / AgentCheckpoint / PersistenceStorageProvider | 功能类似 |
| AgenticScopeJsonCodec (Jackson 序列化) | InMemoryPersistenceStorageProvider / FilePersistenceStorageProvider | Jasmine 支持内存和文件两种存储 |
| AgenticScopeRegistry (全局注册) | 无对应 | langchain4j 有全局 Scope 注册 |
| 无 | RollbackStrategy (RESTART_FROM_NODE/SKIP_NODE/USE_DEFAULT_OUTPUT) | Jasmine 独有的回滚策略 |
| 无 | AgentCheckpointPredicateFilter | Jasmine 独有 |

### 2.11 A2A 协议

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| A2AClientAgent (声明式) | a2a/ (client/server/model/transport/utils) | Jasmine 有完整的 A2A 客户端+服务端实现 |
| A2AClientBuilder / A2AService | 无对应的声明式 API | langchain4j 用注解集成，Jasmine 用独立模块 |

### 2.12 上下文管理

| langchain4j | jasmine-core | 对比 |
|-------------|-------------|------|
| ChatMemory / ChatMemoryProvider | ContextManager + HistoryCompressionStrategy | Jasmine 有更丰富的压缩策略 |
| MessageWindowChatMemory | 无直接对应 | langchain4j 有滑动窗口 |
| 无 | TokenBudget / WholeHistory / LastN / Chunked 四种压缩策略 | Jasmine 独有 |
| 无 | StreamResumeHelper (流式超时续传) | Jasmine 独有 |
| 无 | SystemContextCollector + ContextProvider 体系 | Jasmine 独有的系统上下文注入 |
| 无 | ChatClientRouter (多供应商路由) | Jasmine 独有 |
| 无 | 历史压缩 4 种 CompressionStrategy | Jasmine 独有 |

### 2.13 langchain4j 有但 Jasmine 完全没有的

| 功能 | langchain4j 模块 | 说明 |
|------|-----------------|------|
| Embedding/向量 | langchain4j-core (EmbeddingModel/EmbeddingStore) | 向量嵌入和存储 |
| RAG 管道 | langchain4j-core (ContentRetriever/RetrievalAugmentor) | 检索增强生成 |
| Document 管道 | langchain4j-core (DocumentLoader/DocumentSplitter/DocumentTransformer) | 文档加载/分割/转换 |
| Image/Audio 模型 | langchain4j-core (ImageModel/ScoringModel) | 多模态模型 |
| 分类模型 | langchain4j-core (ClassificationModel) | 文本分类 |
| 模型目录 | langchain4j-core (ModelCatalog) | 模型信息查询 |
| SPI 插件系统 | langchain4j-core (ServiceHelper) | 自动发现和加载 |
| 结构化输出 | langchain4j-core (JsonSchemaElement) | JSON Schema 结构化输出 |
| Guardrails | langchain4j (InputGuardrail/OutputGuardrail) | 输入输出安全护栏 |

### 2.14 Jasmine 有但 langchain4j 没有的

| 功能 | Jasmine 模块 | 说明 |
|------|-------------|------|
| 图执行引擎 | graph/ (GraphAgent/AgentNode/AgentEdge/AgentSubgraph) | 独立的 DAG 执行引擎 |
| GOAP 规划器 | planner/GOAPPlanner | 目标导向行动规划 |
| 移动端本地工具 | 17 个文件工具 + Shell + 计算器 + 时间 + 压缩 | 面向移动端的本地工具 |
| 分类事件系统 | event/ (7 个事件类别) | 细粒度事件分类和过滤 |
| 结构化追踪 | trace/ (TraceEvent 20+ 事件类型) | 比 langchain4j 更完整的追踪 |
| 回滚策略 | snapshot/RollbackStrategy | 3 种回滚策略 |
| 流式超时续传 | StreamResumeHelper | 网络不稳定时自动续传 |
| 系统上下文注入 | SystemContextCollector + 5 种 ContextProvider | 自动拼接环境信息 |
| 多供应商路由 | ChatClientRouter | 运行时切换供应商 |
| 历史压缩 | 4 种 CompressionStrategy | 比 langchain4j 更丰富 |
| Agent 工具预设 | ProviderManager.agentToolPreset | 移动端特有的工具管理 |
| Critic 评估规划 | SimpleLLMWithCriticPlanner | 带评估的规划器 |

## 3. 总结

langchain4j 是一个面向服务端的通用 LLM 框架，强在：
- 声明式 Agent（注解驱动）
- 多 Agent 工作流编排（Sequence/Parallel/Conditional/Supervisor）
- 完整的 RAG/Embedding/Document 管道
- SPI 插件化架构
- 20+ 供应商适配模块

jasmine-core 是一个面向 Android 移动端的 Agent 框架，强在：
- 图执行引擎（GraphAgent）
- GOAP 规划器
- 移动端特有工具（DEX 编辑、文件操作、Shell）
- 流式超时续传
- 系统上下文自动注入
- 分类事件系统和结构化追踪
- 多种历史压缩策略

Jasmine 如果要从 langchain4j 借鉴，最有价值的方向是：
1. 声明式 Agent（@Agent 注解）-- 减少样板代码
2. 工作流编排（Sequence/Parallel/Conditional）-- 补充图引擎之外的编排能力
3. Guardrails（输入/输出安全护栏）-- 提升安全性
4. MCP WebSocket 传输 -- 补充传输层
5. 模型能力声明（Capability）-- 让工具系统感知模型能力

---

## 4. 功能和工具能力对比

### 4.1 工具定义方式

| 能力 | langchain4j | jasmine-core |
|------|-------------|-------------|
| 工具定义 | @Tool 注解标注 Java 方法，自动反射生成 ToolSpecification | Tool 抽象类，手动实现 descriptor + execute |
| 参数定义 | @P 注解标注方法参数，自动推断类型和 JSON Schema | ToolParameterDescriptor 手动声明 name/description/type |
| 参数类型推断 | 自动：从 Java 类型推断（String/int/boolean/enum/List/Map/POJO 全支持） | 手动：ToolParameterType 密封类（String/Int/Float/Boolean/Enum/List/Object） |
| 可选参数 | @P(required=false) | optionalParameters 列表 |
| 参数类型强转 | 自动 coerce：Number->int/long/float/double/BigDecimal，String->Enum，JSON->POJO | 无自动转换，工具内部自行解析 JSON |
| 工具 metadata | 支持（ToolSpecification.metadata，供应商特定元数据） | 不支持 |
| 返回行为 | ReturnBehavior.TO_LLM / IMMEDIATE（立即返回给调用者，不回传 LLM） | 无，结果始终回传 LLM |
| 工具名重复检测 | ToolSpecifications.validateSpecifications 自动检测 | 无（后注册覆盖前注册） |

### 4.2 工具执行能力

| 能力 | langchain4j | jasmine-core |
|------|-------------|-------------|
| 执行方式 | DefaultToolExecutor 通过反射调用 Java 方法 | Tool.execute(arguments: String) 手动解析 JSON |
| 并发执行 | 支持：ToolService.executeToolsConcurrently()，多个 tool_call 并行执行 | 不支持：for 循环顺序执行 |
| 最大迭代次数 | maxSequentialToolsInvocations（默认 100） | maxIterations（默认 10） |
| 幻觉工具名处理 | HallucinatedToolNameStrategy（LLM 调用不存在的工具时的策略） | 返回 "Error: Unknown tool" 字符串 |
| 参数解析错误处理 | ToolArgumentsErrorHandler（可自定义：抛异常或返回错误文本给 LLM 重试） | 无独立处理，catch Exception 返回 "Error: ..." |
| 执行错误处理 | ToolExecutionErrorHandler（可自定义：抛异常或返回错误文本给 LLM 重试） | catch Exception 返回 "Error: ..." |
| 执行前回调 | BeforeToolExecution（beforeToolExecution consumer） | AgentEventListener.onToolCallStart |
| 执行后回调 | ToolExecution（afterToolExecution consumer） | AgentEventListener.onToolCallResult |
| 执行结果类型 | ToolExecutionResult（isError + result 对象 + resultText 懒计算） | ToolResult（callId + name + content 字符串） |
| 错误标记 | ToolExecutionResult.isError（LLM 可感知这是错误结果） | 无独立错误标记，错误信息混在 content 里 |
| 结果对象保留 | ToolExecutionResult.result() 保留原始 Java 对象 | 无，只有字符串 |
| memoryId 传递 | @ToolMemoryId 注解，自动注入 chatMemoryId 到工具方法 | 无 |
| InvocationContext | 工具方法可接收 InvocationContext（含 memoryId + invocationParameters） | 无 |

### 4.3 工具提供/注册机制

| 能力 | langchain4j | jasmine-core |
|------|-------------|-------------|
| 静态注册 | AiServices.tools(Object...) 传入带 @Tool 的对象 | ToolRegistry.register(tool) / registerAll() |
| 动态提供 | ToolProvider 接口：每次 LLM 调用时动态决定提供哪些工具 | 无，构建时固定 |
| 按请求过滤 | ToolProviderRequest 包含 UserMessage + memoryId，可按用户/消息动态过滤 | 无 |
| MCP 工具过滤 | McpToolProvider.filter(BiPredicate) 按 client + spec 过滤 | 无 |
| MCP 工具名映射 | McpToolProvider.toolNameMapper / toolSpecificationMapper | 无 |
| MCP 资源转工具 | ResourcesAsTools（MCP 资源自动暴露为 list_resources + get_resource 工具） | 无 |
| 工具分组开关 | 无内置机制 | ToolConfigActivity 分组开关（calculator/file_tools 等） |
| Agent 工具预设 | 无 | ProviderManager.agentToolPreset（Agent 模式独立工具配置） |

### 4.4 Agent Loop 能力

| 能力 | langchain4j | jasmine-core |
|------|-------------|-------------|
| 非流式循环 | ToolService.executeInferenceAndToolsLoop | ToolExecutor.executeLoop |
| 流式循环 | StreamingToolService（类似机制） | ToolExecutor.executeStreamLoop |
| 上下文压缩 | 无内置（依赖 ChatMemory 窗口） | compressionStrategy 自动压缩（TokenBudget/WholeHistory/LastN/Chunked） |
| 显式完成信号 | 无 | attempt_completion 工具 + AgentCompletionSignal |
| 思考内容转发 | 无 | onThinking 回调 |
| 立即返回 | ReturnBehavior.IMMEDIATE（工具结果直接返回调用者） | 无 |
| 取消支持 | 无内置 | coroutineContext.ensureActive() 协程取消 |
| 追踪集成 | 无内置（需外部 AgentListener） | Tracing 内置（20+ TraceEvent 类型） |

### 4.5 错误恢复能力

| 能力 | langchain4j | jasmine-core |
|------|-------------|-------------|
| 参数错误 | ToolArgumentsErrorHandler：可选抛异常或返回文本让 LLM 重试 | 无独立处理 |
| 执行错误 | ToolExecutionErrorHandler：可选抛异常或返回文本让 LLM 重试 | catch 返回 "Error: ..." |
| 幻觉工具名 | HallucinatedToolNameStrategy：可自定义策略 | 返回 "Error: Unknown tool" |
| Agent 级错误恢复 | ErrorHandler + ErrorContext + ErrorRecoveryResult（声明式 Agent） | 无 |
| 快照回滚 | 无 | RollbackStrategy（RESTART_FROM_NODE/SKIP_NODE/USE_DEFAULT_OUTPUT） |

### 4.6 Jasmine 独有的工具能力

| 能力 | 说明 |
|------|------|
| 工具分组管理 | 8 个分组开关，一键启用/禁用整组工具 |
| Agent 工具预设 | Agent 模式独立的工具配置，与普通模式分离 |
| 显式完成信号 | attempt_completion 工具让 LLM 主动声明任务完成 |
| 流式超时续传 | StreamResumeHelper 网络中断自动续传 |
| 协程取消 | 随时取消正在执行的 Agent Loop |
| 内置压缩 | Agent Loop 内自动检测并压缩上下文 |
| Shell 执行策略 | 手动确认/黑名单/白名单三种策略 |
| 工作区隔离 | 文件工具基于用户选择的工作区路径 |

### 4.7 langchain4j 独有的工具能力

| 能力 | 说明 |
|------|------|
| 注解驱动 | @Tool + @P 自动生成工具描述，零样板代码 |
| 自动类型转换 | LLM 返回的参数自动 coerce 到 Java 类型 |
| 并发工具执行 | 多个 tool_call 并行执行 |
| 动态工具提供 | ToolProvider 按请求动态决定工具集 |
| 错误分类处理 | 参数错误和执行错误分开处理，可自定义策略 |
| 幻觉工具策略 | LLM 调用不存在工具时的可配置策略 |
| 立即返回 | ReturnBehavior.IMMEDIATE 跳过 LLM 后处理 |
| 错误标记 | isError 标记让 LLM 感知工具执行失败 |
| 结果对象保留 | 保留原始 Java 对象，不仅仅是字符串 |
| memoryId 注入 | @ToolMemoryId 自动注入会话 ID 到工具 |
| MCP 工具过滤/映射 | 按名称/规则过滤和重命名 MCP 工具 |
| MCP 资源转工具 | 自动将 MCP 资源暴露为工具 |
| 工具 metadata | 供应商特定元数据（如 Anthropic cache_control） |
