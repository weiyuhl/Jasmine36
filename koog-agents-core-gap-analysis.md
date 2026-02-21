# koog agents-core vs jasmine-core 差异分析

> 对比版本: koog 0.6.2 agents-core (commonMain) vs jasmine-core agent-tools graph 模块
> 生成时间: 2026-02-21

---

## 一、已完整移植的模块

| jasmine-core 文件 | 对应 koog 源 | 状态 |
|---|---|---|
| ToolResultKind.kt | environment/ToolResultKind.kt + ReceivedToolResult | 完整 |
| AgentState.kt | agent/AIAgent.Companion.State | 完整 |
| ToolCalls.kt | agent/AIAgentSimpleStrategies.ToolCalls | 完整 |
| AgentEnvironment.kt | environment/AIAgentEnvironment + GenericAgentEnvironment | 完整 |
| FunctionalStrategy.kt | agent/AIAgentFunctionalStrategy | 完整 |
| FunctionalAgent.kt | agent/FunctionalAIAgent | 完整 |
| AgentService.kt | agent/AIAgentService + GraphAIAgentService + FunctionalAIAgentService | 完整 |
| AgentAsTool.kt | agent/AIAgentTool | 完整 |
| AgentGraphContext.kt | agent/context/AIAgentGraphContext + AIAgentContext | 完整 |
| AgentNode.kt | agent/entity/AIAgentNode + StartNode + FinishNode + AIAgentNodeDelegate | 完整 |
| AgentEdge.kt | agent/entity/AIAgentEdge + dsl/builder/AIAgentEdgeBuilder | 完整 |
| GraphStrategyBuilder.kt | dsl/builder/AIAgentGraphStrategyBuilder + AIAgentSubgraphBuilder | 完整 |
| GraphAgent.kt | agent/GraphAIAgent | 完整 |
| AgentStrategy.kt | agent/entity/AIAgentGraphStrategy | 完整 |
| AgentSubgraph.kt | agent/entity/AIAgentSubgraph | 完整 |
| PredefinedStrategies.kt | agent/AIAgentSimpleStrategies (singleRunStrategy 3种模式 + stream版) | 完整 |
| HistoryCompressionStrategy.kt | dsl/extension/HistoryCompressionStrategies.kt | 完整 (jasmine额外增加了TokenBudget) |
| LLMSession.kt 中的 LLM Actions | dsl/extension/AIAgentLLMActions.kt | 完整 |

---

## 二、预定义节点移植状态

来源: `koog dsl/extension/AIAgentNodes.kt`
目标: `jasmine PredefinedNodes.kt`

jasmine 共有 22 个预定义节点，koog 共有约 24 个节点。

### 已完成移植的节点

原始移植 (10个):

| 节点名 | 功能说明 |
|---|---|
| nodeLLMRequest | 追加用户消息并获取LLM响应 |
| nodeLLMRequestStreaming | 流式请求LLM(回调式) |
| nodeLLMRequestWithoutTools | 不允许工具调用的LLM请求 |
| nodeAppendPrompt | 向prompt追加消息 |
| nodeDoNothing | 空操作，直接透传 |
| nodeExecuteTool | 执行单个工具调用 |
| nodeExecuteMultipleTools | 执行多个工具调用(支持并行) |
| nodeLLMSendToolResult | 发送单个工具结果并请求LLM |
| nodeLLMSendMultipleToolResults | 发送多个工具结果并请求LLM |
| nodeLLMSendMultipleToolResultsStreaming | 流式发送多个工具结果 |

后续移植 (12个):

| 节点名 | 功能说明 |
|---|---|
| nodeLLMRequestOnlyCallingTools | 强制LLM只能调用工具(ToolChoice.Required) |
| nodeLLMRequestMultiple | 获取多个LLM响应 |
| nodeLLMRequestMultipleOnlyCallingTools | 多响应 + 只能调用工具 |
| nodeLLMRequestForceOneTool | 强制LLM使用指定工具(ToolChoice.Named) |
| nodeLLMCompressHistory | 历史压缩节点 |
| nodeLLMRequestStructured | 请求LLM返回结构化JSON输出 |
| nodeExecuteMultipleToolsAndSendResults | 执行多工具并发送结果给LLM |
| nodeLLMSendToolResultOnlyCallingTools | 发送工具结果 + 强制只能调用工具 |
| nodeLLMSendMultipleToolResultsMultiple | 发送多工具结果，获取多响应 |
| nodeLLMSendMultipleToolResultsOnlyCallingTools | 发送多工具结果 + 强制只能调用工具 |
| nodeExecuteSingleTool | 直接调用指定工具(不经过LLM选择) |
| nodeLLMRequestStreamingAndSendResults | 流式请求LLM并收集结果更新prompt |

仍未移植（低优先级 / 架构差异大）:

| 节点名 | koog 函数签名 | 功能说明 |
|---|---|---|
| nodeLLMModerateMessage | `(Message) -> ModeratedMessage` | 内容审核节点，需要 moderate() API |
| nodeLLMRequestStreaming (Flow) | `(String) -> Flow<StreamFrame>` | Flow-based流式请求(koog用Flow，jasmine用回调) |
| nodeSetStructuredOutput | `(TInput) -> TInput` | 设置结构化输出schema(需要StructuredOutputConfig) |
| nodeLLMSendMessageOnlyCallingTools | `(String) -> Message.Response` | 同nodeLLMRequestOnlyCallingTools，koog别名 |
| nodeLLMSendMessageForceOneTool | `(String) -> Message.Response` | 同nodeLLMRequestForceOneTool，koog别名 |

### 移植难度评估（仍未移植的项目）

- nodeLLMModerateMessage: 需要 moderate() API，jasmine 没有，需要新增
- nodeLLMRequestStreaming (Flow): koog 用 Kotlin Flow，jasmine 用回调式。架构差异较大
- nodeSetStructuredOutput: 需要 StructuredOutputConfig，jasmine 没有完整的结构化输出配置系统
- nodeLLMSendMessageOnlyCallingTools / nodeLLMSendMessageForceOneTool: koog 别名，功能已由现有节点覆盖

---

## 三、边条件移植状态

来源: `koog dsl/extension/AIAgentEdges.kt`
目标: `jasmine AgentEdge.kt`

### 已完成移植的边条件

| 边条件 | koog 签名 | jasmine 适配方式 | 状态 |
|---|---|---|---|
| onIsInstance(KClass) | `EdgeBuilder -> EdgeBuilder` | 直接移植，reified 类型检查 | [已完成] |
| onToolCall(block) | `EdgeBuilder -> EdgeBuilder<ToolCall>` | 适配为 ChatResult.hasToolCalls | [已完成] |
| onToolCall(tool: Tool) | `EdgeBuilder -> EdgeBuilder<ToolCall>` | 适配为 onToolCall(toolName: String) | [已完成] |
| onToolCall(tool, argsCondition) | `EdgeBuilder -> EdgeBuilder<ToolCall>` | 适配为 onToolCallWithArgs(toolName, block) | [已完成] |
| onToolNotCalled(tool) | `EdgeBuilder -> EdgeBuilder<ToolCall>` | 适配为 onToolNotCalled(toolName: String) | [已完成] |
| onToolResult(tool, condition) | `EdgeBuilder -> EdgeBuilder<ReceivedToolResult>` | 适配为 onToolResult(toolName, block) | [已完成] |
| onAssistantMessage(block) | `EdgeBuilder -> EdgeBuilder<String>` | 适配为 ChatResult 无工具调用判断 | [已完成] |
| onReasoningMessage(block) | `EdgeBuilder -> EdgeBuilder<Reasoning>` | 适配为 ChatResult.thinking 判断 | [已完成] |
| onMultipleToolCalls(block) | `EdgeBuilder<List<Response>> -> EdgeBuilder<List<ToolCall>>` | 适配为 List<ChatResult> 过滤 | [已完成] |
| onMultipleToolResults(block) | `EdgeBuilder -> EdgeBuilder<List<ReceivedToolResult>>` | 直接移植 | [已完成] |
| onMultipleAssistantMessages(block) | `EdgeBuilder<List<Response>> -> EdgeBuilder<List<Assistant>>` | 适配为 List<ChatResult> 过滤 | [已完成] |
| onMultipleReasoningMessages(block) | `EdgeBuilder<List<Response>> -> EdgeBuilder<List<Reasoning>>` | 适配为 List<ChatResult> thinking 过滤 | [已完成] |
| onToolSuccess(block) | jasmine 自有 | 基于 ToolResultKind.Success 过滤 | [已完成] |
| onToolFailure(block) | jasmine 自有 | 基于 ToolResultKind.Failure 过滤 | [已完成] |
| onToolValidationError(block) | jasmine 自有 | 基于 ToolResultKind.ValidationError 过滤 | [已完成] |

### 不可移植的边条件（架构差异）

| 边条件 | koog 签名 | 原因 |
|---|---|---|
| onSuccessful(condition) | `EdgeBuilder<SafeTool.Result> -> EdgeBuilder<Success>` | 依赖 SafeTool.Result 类型，jasmine 用 ToolResultKind 替代 |
| onFailure(condition) | `EdgeBuilder<SafeTool.Result> -> EdgeBuilder<Failure>` | 依赖 SafeTool.Result 类型，jasmine 用 ToolResultKind 替代 |
| onAssistantMessageWithMedia(block) | `EdgeBuilder -> EdgeBuilder<List<Attachment>>` | 依赖 ContentPart.Attachment，jasmine ChatResult 无附件概念 |

---

## 四、已完成移植 -- FunctionalContext 扩展函数

来源: `koog dsl/extension/AIAgentFunctionalContextExt.kt`
目标: `jasmine FunctionalContextExt.kt`

koog 的 20 个 FunctionalContext 扩展函数已全部移植完成:

| 扩展函数 | 功能 | 状态 |
|---|---|---|
| requestLLM(message, allowToolCalls) | 追加用户消息并请求LLM | [已完成] |
| onAssistantMessage(response, action) | 如果是助手消息则执行action | [已完成] |
| containsToolCalls() | 检查响应列表是否包含工具调用 | [已完成] |
| asAssistantMessageOrNull() | 安全转换为助手消息 | [已完成] |
| asAssistantMessage() | 强制转换为助手消息 | [已完成] |
| onMultipleToolCalls(response, action) | 如果有多个工具调用则执行action | [已完成] |
| extractToolCalls(response) | 从响应列表提取工具调用 | [已完成] |
| onMultipleAssistantMessages(response, action) | 如果有多个助手消息则执行action | [已完成] |
| latestTokenUsage() | 获取最新token用量 | [已完成] |
| requestLLMStructured(message, examples) | 请求结构化输出 | [已完成] |
| requestLLMStreaming(message) | 流式请求(jasmine适配为回调式requestLLMStream) | [已完成] |
| requestLLMMultiple(message) | 多响应请求 | [已完成] |
| requestLLMOnlyCallingTools(message) | 只能调用工具的请求 | [已完成] |
| requestLLMForceOneTool(message, tool) | 强制使用指定工具 | [已完成] |
| executeTool(toolCall) | 执行工具 | [已完成] |
| executeMultipleTools(toolCalls, parallel) | 执行多个工具 | [已完成] |
| sendToolResult(toolResult) | 发送工具结果并请求LLM | [已完成] |
| sendMultipleToolResults(results) | 发送多个工具结果 | [已完成] |
| executeSingleTool(tool, args) | 直接调用指定工具 | [已完成] |
| compressHistory(strategy) | 压缩历史 | [已完成] |

jasmine 额外提供的扩展函数（koog 没有的）:
- `requestLLMStream(message, onChunk, onThinking)` -- 回调式流式请求
- `requestLLMStructured<T>(message, examples)` -- inline reified 版本
- `onToolCalls(result, action)` -- 工具调用判断（与 onAssistantMessage 对称）
- `estimateTokenUsage()` -- 估算 token 数
- `sendMultipleToolResultsMultiple(results)` -- 发送多工具结果并获取多响应

---

## 五、已完成移植 -- ToolSelectionStrategy

来源: `koog agent/entity/AIAgentSubgraph.kt` 中的 sealed interface
目标: `jasmine AgentSubgraph.kt`

| 策略 | 功能 | 状态 |
|---|---|---|
| ALL | 使用所有可用工具(默认) | [已完成] |
| NONE | 不使用任何工具 | [已完成] |
| Tools(list) | 使用指定的工具列表 | [已完成] |
| AutoSelectForTask(description) | LLM根据子任务描述自动选择相关工具 | [已完成] |

---

## 六、已完成移植 -- ExecutionPointNode (强制执行点)

来源: `koog agent/entity/ExecutionPointNode.kt`
目标: `jasmine AgentSubgraph.kt`

| 接口方法 | 功能 | 状态 |
|---|---|---|
| getExecutionPoint() | 获取当前强制执行点 | [已完成] |
| resetExecutionPoint() | 重置执行点 | [已完成] |
| enforceExecutionPoint(node, input) | 设置强制执行点(跳转到指定节点) | [已完成] |

---

## 七、已完成移植 -- Feature/Pipeline 系统

来源: `koog agents-core/feature/` (~30+ 文件)
目标: `jasmine feature/` (17 个新文件)

将 jasmine 的简单 Tracing 系统增强为完整的 Feature/Pipeline 插件系统，与 koog 架构对齐。

### 核心接口和类

| jasmine 文件 | 对应 koog 源 | 状态 |
|---|---|---|
| FeatureKey.kt | AIAgentStorageKey | [已完成] |
| AgentFeature.kt | AIAgentFeature / AIAgentGraphFeature / AIAgentFunctionalFeature | [已完成] |
| AgentLifecycleEventType.kt | AgentLifecycleEventType (25种事件类型，7大类) | [已完成] |
| AgentLifecycleEventContext.kt | AgentLifecycleEventContext | [已完成] |
| FeatureConfig.kt | FeatureConfig (messageProcessors + eventFilter) | [已完成] |
| FeatureMessage.kt | FeatureMessage (Message/Event 两种类型) | [已完成] |
| FeatureMessageProcessor.kt | FeatureMessageProcessor (messageFilter + processMessage) | [已完成] |

### Pipeline 实现

| jasmine 文件 | 对应 koog 源 | 状态 |
|---|---|---|
| AgentPipeline.kt | AIAgentPipeline (Feature注册/卸载 + 所有拦截器 + 事件触发) | [已完成] |
| AgentGraphPipeline.kt | AIAgentGraphPipeline (增加Node/Subgraph事件) | [已完成] |
| AgentFunctionalPipeline.kt | AIAgentFunctionalPipeline | [已完成] |

### Handler 包 (7大类)

| jasmine handler 文件 | 对应 koog handler 包 | 包含内容 | 状态 |
|---|---|---|---|
| AgentEventHandler.kt | handler/agent/ | 5个上下文 + 4个Handler + 1个容器 | [已完成] |
| StrategyEventHandler.kt | handler/strategy/ | 2个上下文 + 2个Handler + 1个容器 | [已完成] |
| LLMCallEventHandler.kt | handler/llm/ | 2个上下文 + 2个Handler + 1个容器 | [已完成] |
| ToolCallEventHandler.kt | handler/tool/ | 4个上下文 + 4个Handler + 1个容器 | [已完成] |
| LLMStreamingEventHandler.kt | handler/streaming/ | 4个上下文 + 4个Handler + 1个容器 | [已完成] |
| NodeExecutionEventHandler.kt | handler/node/ | 3个上下文 + 3个Handler + 1个容器 | [已完成] |
| SubgraphExecutionEventHandler.kt | handler/subgraph/ | 3个上下文 + 3个Handler + 1个容器 | [已完成] |

### 集成点

Pipeline 已集成到以下现有文件中:

- AgentGraphContext.kt -- 新增 `pipeline: AgentPipeline?` 字段
- GraphAgent.kt -- 新增 `pipeline: AgentGraphPipeline?` 参数，触发 Agent 生命周期事件
- FunctionalAgent.kt -- 新增 `pipeline: AgentFunctionalPipeline?` 参数，触发 Agent 生命周期事件
- AgentSubgraph.kt -- 触发 Node/Subgraph 执行事件

Pipeline 事件与现有 Tracing 事件并行触发，完全向后兼容。

### 未移植的 koog Feature/Pipeline 子项（架构差异）

| koog 子项 | 原因 |
|---|---|
| AgentEnvironmentTransformingHandler | koog 用于在 Agent 启动前变换环境，jasmine 环境创建方式不同 |
| installFeaturesFromSystemConfig | koog 通过环境变量/VM参数自动安装系统Feature，jasmine 不需要 |
| Debugger Feature | koog 内置调试Feature，jasmine 可通过 Tracing 实现类似功能 |
| FeatureSystemVariables | koog 的环境变量配置，jasmine 不需要 |

---

## 八、已完成移植 -- LLM Session 读写分离

来源: `koog agents-core/agent/session/` (3 个文件)
目标: `jasmine LLMSession.kt` (重构为 sealed class + 2 个子类)

将 jasmine 的单一 LLMSession 重构为 koog 的读写分离架构。

### 架构对比

| koog | jasmine (移植后) | 说明 |
|---|---|---|
| AIAgentLLMSession (sealed base) | LLMSession (sealed base) | 所有 LLM 请求方法(只读)，不自动追加响应 |
| AIAgentLLMReadSession | LLMReadSession | 纯只读会话，不能修改 prompt/tools/model |
| AIAgentLLMWriteSession | LLMWriteSession | 可写会话，override 请求方法自动追加响应 |

### LLMSession (sealed base) -- 只读请求方法

| 方法 | 功能 | 状态 |
|---|---|---|
| requestLLM() | 发送请求(带工具)，不自动追加 | [已完成] |
| requestLLMWithoutTools() | 发送请求(不带工具)，不自动追加 | [已完成] |
| requestLLMOnlyCallingTools() | 强制只能调用工具 | [已完成] |
| requestLLMForceOneTool(toolName) | 强制使用指定工具 | [已完成] |
| requestLLMMultiple() | 多响应请求 | [已完成] |
| requestLLMMultipleOnlyCallingTools() | 多响应 + 只能调用工具 | [已完成] |
| requestLLMMultipleWithoutTools() | 多响应(不带工具) | [已完成] |
| requestLLMStream(onChunk, onThinking) | 流式请求(带工具) | [已完成] |
| requestLLMStreamWithoutTools(onChunk, onThinking) | 流式请求(不带工具) | [已完成] |
| requestLLMStructured(serializer, examples) | 结构化 JSON 输出 | [已完成] |

### LLMWriteSession -- 新增的写操作方法

| 方法 | 对应 koog | 状态 |
|---|---|---|
| appendPrompt(body) | AIAgentLLMWriteSession.appendPrompt | [已完成] |
| rewritePrompt(body) | AIAgentLLMWriteSession.rewritePrompt | [已完成] |
| changeModel(newModel) | AIAgentLLMWriteSession.changeModel | [已完成] |
| clearHistory() | jasmine 自有 | [已完成] |
| leaveLastNMessages(n) | jasmine 自有 | [已完成] |
| dropLastNMessages(n) | jasmine 自有 | [已完成] |
| leaveMessagesFromTimestamp(ts) | jasmine 自有 | [已完成] |
| dropTrailingToolCalls() | jasmine 自有 | [已完成] |
| setToolChoiceAuto/Required/None/Named | jasmine 自有 | [已完成] |
| 所有 requestLLM 方法 override | 自动追加响应到 prompt | [已完成] |

### 便捷函数

| 函数 | 功能 | 状态 |
|---|---|---|
| ChatClient.session {} | 创建 LLMWriteSession 并执行 | [已完成] |
| ChatClient.readSession {} | 创建 LLMReadSession 并执行 | [已完成] |

### 集成点更新

- AgentGraphContext -- session 类型改为 LLMWriteSession，新增 readSession: LLMReadSession
- GraphAgent -- 创建 LLMWriteSession + LLMReadSession
- FunctionalAgent -- 创建 LLMWriteSession + LLMReadSession
- ToolExecutor -- 使用 LLMWriteSession
- HistoryCompressionStrategy -- compress() 参数类型改为 LLMWriteSession
- 所有测试文件已同步更新

### 未移植的 koog WriteSession 功能（架构差异）

| koog 功能 | 原因 |
|---|---|
| callTool / findTool | 依赖 SafeTool 类型系统，jasmine 用 AgentEnvironment.executeTool 替代 |
| toParallelToolCalls (Flow) | 依赖 SafeTool，jasmine 无此类型 |
| changeLLMParams(LLMParams) | koog 有独立 LLMParams 类，jasmine 用 SamplingParams 直接在 Prompt 上 |
| ActiveProperty 委托 | koog 用 ActiveProperty 实现属性活跃检查，jasmine 用简单 var + checkActive() |

---

## 九、已完成移植 -- AgentStorage (类型化并发安全存储)

来源: `koog agents-core/agent/entity/AIAgentStorage.kt`
目标: `jasmine AgentStorage.kt` + `AgentGraphContext.kt` 重构

将 jasmine 的 `MutableMap<String, Any?>` 替换为 koog 的类型化并发安全存储。

### 架构对比

| koog | jasmine (移植后) | 说明 |
|---|---|---|
| AIAgentStorageKey<T : Any>(name) | AgentStorageKey<T : Any>(name) | 类型化存储 key |
| createStorageKey<T>(name) | createStorageKey<T>(name) | 工厂函数 |
| AIAgentStorage (Mutex) | AgentStorage (Mutex) | 并发安全 key-value 存储 |

### AgentStorage 方法

| 方法 | 功能 | 状态 |
|---|---|---|
| set(key, value) | suspend, 类型安全设置值 | [已完成] |
| get(key) | suspend, 返回 T? | [已完成] |
| getValue(key) | suspend, 返回 T (不存在则抛异常) | [已完成] |
| remove(key) | suspend, 返回被移除的值 | [已完成] |
| toMap() | suspend, 快照 | [已完成] |
| putAll(map) | suspend, 批量添加 | [已完成] |
| clear() | suspend, 清空 | [已完成] |
| copy() | internal suspend, 深拷贝 (用于 fork) | [已完成] |

### 集成点更新

- AgentGraphContext -- storage 类型从 MutableMap<String, Any?> 改为 AgentStorage
- AgentGraphContext.get/put -- 改为 suspend 方法，使用 AgentStorageKey<T>
- AgentGraphContext.fork() -- 改为 suspend，使用 storage.copy()
- PredefinedStrategies -- 所有回调 key 从 String 常量改为 AgentStorageKey<T> (KEY_ON_CHUNK, KEY_ON_THINKING, KEY_ON_TOOL_CALL_START, KEY_ON_TOOL_CALL_RESULT, KEY_ON_NODE_ENTER, KEY_ON_NODE_EXIT, KEY_ON_EDGE)
- PredefinedNodes -- 回调获取改为 storage.get(PredefinedStrategies.KEY_ON_CHUNK) 等
- GraphAgent.runWithCallbacks -- 回调存储改为 storage.set(KEY, value)
- Persistence -- 恢复信息存储改为 AgentStorageKey (KEY_RESTORED_NODE, KEY_RESTORED_INPUT)
- GraphAgentTest -- 测试用例更新为使用 AgentStorageKey

---

## 十、已完成移植 -- AgentExecutionInfo / AgentNodePath (执行路径追踪)

来源: `koog agents-core/agent/execution/AgentExecutionInfo.kt` + `AgentNodePath.kt`
目标: `jasmine AgentExecutionInfo.kt` + `AgentGraphContext.kt` 更新

将 koog 的执行路径追踪系统移植到 jasmine。

### 架构对比

| koog | jasmine (移植后) | 说明 |
|---|---|---|
| DEFAULT_AGENT_PATH_SEPARATOR | DEFAULT_AGENT_PATH_SEPARATOR | 默认路径分隔符 "/" |
| path(vararg parts, separator) | path(vararg parts, separator) | 路径拼接工具函数 |
| AgentExecutionInfo(parent, partName) | AgentExecutionInfo(parent, partName) | @Serializable data class，层级执行信息 |
| AgentExecutionInfo.path(separator) | AgentExecutionInfo.path(separator) | 构建完整路径字符串 |
| AIAgentContext.with(executionInfo, block) | AgentGraphContext.with(executionInfo, block) | 临时切换执行信息的扩展函数 |
| AIAgentContext.with(partName, block) | AgentGraphContext.with(partName, block) | 创建子级执行信息的扩展函数 |

### 集成点更新

- AgentGraphContext -- 新增 `executionInfo: AgentExecutionInfo` 构造参数 + `currentExecutionInfo: var` 属性
- AgentGraphContext.fork() -- 复制 executionInfo
- GraphAgent.run() -- 创建初始 `AgentExecutionInfo(null, agentId)`
- GraphAgent.runWithCallbacks() -- 同上
- FunctionalAgent.run() -- 同上

### 测试

- AgentExecutionInfoTest -- 移植自 koog 的 8 个测试 + 1 个 path 工具函数测试

---

## 十一、架构差异 -- 不建议直接移植

以下是 koog 和 jasmine 的架构设计差异，属于有意的简化，不建议直接移植:

| koog 模块 | jasmine 对应 | 差异说明 |
|---|---|---|
| SafeTool<TArgs, TResult> | 无 | koog 的 Tool 有类型化的 Args/Result，jasmine 的 Tool 是 execute(String)->String |
| LLM Session 读写分离 (ReadSession/WriteSession) | LLMReadSession / LLMWriteSession | koog 分离读写权限，jasmine 已完成移植 |
| AIAgentConfig / AIAgentConfigBase | 直接参数 | koog 用配置对象，jasmine 用构造函数参数 |
| AIAgentStorage | AgentStorage (AgentStorageKey + Mutex) | koog 有独立存储类，jasmine 已完成移植 |
| AIAgentStateManager (Mutex) | ManagedAgent.state | koog 有 Mutex 保护的状态管理，jasmine 直接设置 |
| AgentContextData / RollbackStrategy | 无 | checkpoint/rollback 机制 |
| AgentExecutionInfo / AgentNodePath | AgentExecutionInfo + with 扩展函数 | 已完成移植 |
| ContextualAgentEnvironment | 无 | 带 pipeline 事件的环境包装 |
| SubgraphMetadata | 无 | 节点映射元数据 |
| Utils (Option, RWLock, MutexCheck) | 无 | 通用工具类 |
| TerminationTool (NAME="__terminate__") | ToolExecutor.COMPLETION_TOOL_NAME="attempt_completion" | 名称不同，功能类似 |
| Message 类型层次 (Assistant, Reasoning, Tool.Call) | Message 密封类层次 + ChatMessage 互转 | 已完成移植 |

---

## 十一.一、已完成移植 -- Message 类型层次 (密封类层次)

来源: `koog prompt-model/message/Message.kt` + `ContentPart.kt` + `MessageMetaInfo`
目标: `jasmine prompt-model/` (3 个新文件 + 5 个修改文件)

### 新增文件

| 文件 | 说明 |
|---|---|
| MessageRole.kt | 消息角色枚举 (System, User, Assistant, Reasoning, Tool) |
| MessageMetaInfo.kt | 消息元数据 (RequestMetaInfo, ResponseMetaInfo) |
| Message.kt | Message 密封接口 + User/System/Assistant/Reasoning/Tool.Call/Tool.Result |

### 修改文件

| 文件 | 新增内容 |
|---|---|
| ChatMessage.kt | toMessage(), toChatMessage(), toMessages(), toChatMessages() 互转方法 |
| ChatResult.kt | toAssistantMessage(), toMessages() 转换方法 |
| Prompt.kt | typedMessages 属性, fromMessages() 工厂方法 |
| PromptBuilder.kt | message(Message), messages(List<Message>), reasoning() |
| LLMSession.kt | requestLLMAsMessage(), requestLLMWithoutToolsAsMessage(), requestLLMAsMessages(), appendMessage(), appendMessages() |
| AgentEdge.kt | onAssistant, onToolCallMessage, onReasoning 边条件 (Message.Response 版本) |
| FunctionalContextExt.kt | requestLLMAsMessage(), requestLLMWithoutToolsAsMessage(), appendMessage(), appendMessages() |
| PredefinedNodes.kt | nodeLLMRequestAsMessage, nodeLLMRequestAsMessages |
| AgentCheckpoint.kt | typedMessageHistory 属性 |
| ConversationRepository.kt | addTypedMessage(), addTypedMessages(), getTypedMessages(), observeTypedMessages() |

### 简化决策

| koog 特性 | jasmine 处理 |
|---|---|
| ContentPart (Text/Image/Video/Audio/File) | 只保留文本内容，不移植多媒体附件 |
| kotlinx.datetime.Instant | 使用 Long 时间戳 |
| ResponseMetaInfo.additionalInfo (deprecated) | 不移植 |
| Message.hasAttachments() / hasOnlyTextContent() | 不移植 (依赖 ContentPart.Attachment) |

### 测试

- MessageTest -- 28 个测试 (角色、类型层次、属性验证)
- MessageConversionTest -- 22 个测试 (ChatMessage/ChatResult <-> Message 互转、往返一致性)

---

## 十二、移植优先级建议

### 高优先级 (实用性强，移植难度低) -- 全部已完成

1. FunctionalContext 扩展函数 (20个) [已完成]
2. nodeLLMRequestForceOneTool [已完成]
3. nodeLLMRequestOnlyCallingTools [已完成]
4. nodeLLMRequestStructured [已完成]
5. nodeLLMCompressHistory [已完成]
6. ToolSelectionStrategy (ALL/NONE/Tools) [已完成]

### 中优先级 (有价值，需要一定工作量) -- 全部已完成

7. onIsInstance 边条件 [已完成]
8. onToolCall(tool) / onToolNotCalled(tool) / onToolCallWithArgs(tool, args) [已完成]
9. onMultipleToolCalls / onMultipleAssistantMessages / onMultipleReasoningMessages [已完成]
10. onToolResult(tool, condition) [已完成]
11. nodeLLMRequestMultiple [已完成]
12. nodeExecuteSingleTool [已完成]
13. nodeLLMRequestStreamingAndSendResults [已完成]
14. ExecutionPointNode [已完成]
15. ToolSelectionStrategy.AutoSelectForTask [已完成]

### 大型子系统 -- 已完成

16. Feature/Pipeline 系统 (17个新文件，完整插件/事件/处理器管道) [已完成]
17. LLM Session 读写分离 (LLMSession sealed base + LLMReadSession + LLMWriteSession) [已完成]
18. AgentStorage 类型化并发安全存储 (AgentStorageKey + Mutex) [已完成]
19. AgentExecutionInfo / AgentNodePath 执行路径追踪 [已完成]
20. Message 类型层次 (密封类层次 + ChatMessage 互转 + 全模块集成) [已完成]

### 低优先级 (依赖架构变更或使用场景有限) -- 不移植

20. SafeTool 类型系统 -- 需要重构 Tool 基类，架构差异
21. onSuccessful / onFailure -- 依赖 SafeTool.Result，jasmine 用 ToolResultKind 替代
22. onAssistantMessageWithMedia -- 依赖 ContentPart.Attachment，jasmine 无附件概念
23. nodeLLMModerateMessage -- 需要 moderate() API
24. Flow-based streaming nodes -- 架构差异大，jasmine 用回调式
25. nodeSetStructuredOutput -- 需要 StructuredOutputConfig
