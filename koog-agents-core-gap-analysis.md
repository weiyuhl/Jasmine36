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

## 七、架构差异 -- 不建议直接移植

以下是 koog 和 jasmine 的架构设计差异，属于有意的简化，不建议直接移植:

| koog 模块 | jasmine 对应 | 差异说明 |
|---|---|---|
| SafeTool<TArgs, TResult> | 无 | koog 的 Tool 有类型化的 Args/Result，jasmine 的 Tool 是 execute(String)->String |
| Feature/Pipeline 系统 (~30+文件) | Tracing 系统 | koog 有完整的插件/事件/处理器管道，jasmine 用更简单的 Tracing |
| LLM Session 读写分离 (ReadSession/WriteSession) | 单一 LLMSession | koog 分离读写权限，jasmine 合一 |
| AIAgentConfig / AIAgentConfigBase | 直接参数 | koog 用配置对象，jasmine 用构造函数参数 |
| AIAgentStorage | MutableMap<String, Any?> | koog 有独立存储类，jasmine 直接用 Map |
| AIAgentStateManager (Mutex) | ManagedAgent.state | koog 有 Mutex 保护的状态管理，jasmine 直接设置 |
| AgentContextData / RollbackStrategy | 无 | checkpoint/rollback 机制 |
| AgentExecutionInfo / AgentNodePath | 无 | 执行路径追踪 |
| ContextualAgentEnvironment | 无 | 带 pipeline 事件的环境包装 |
| SubgraphMetadata | 无 | 节点映射元数据 |
| Utils (Option, RWLock, MutexCheck) | 无 | 通用工具类 |
| TerminationTool (NAME="__terminate__") | ToolExecutor.COMPLETION_TOOL_NAME="attempt_completion" | 名称不同，功能类似 |
| Message 类型层次 (Assistant, Reasoning, Tool.Call) | ChatMessage/ChatResult 扁平结构 | koog 用密封类层次，jasmine 用角色字符串 |

---

## 八、移植优先级建议

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

### 低优先级 (依赖架构变更或使用场景有限) -- 不移植

16. SafeTool 类型系统 -- 需要重构 Tool 基类，架构差异
17. onSuccessful / onFailure -- 依赖 SafeTool.Result，jasmine 用 ToolResultKind 替代
18. onAssistantMessageWithMedia -- 依赖 ContentPart.Attachment，jasmine 无附件概念
19. nodeLLMModerateMessage -- 需要 moderate() API
20. Flow-based streaming nodes -- 架构差异大，jasmine 用回调式
21. nodeSetStructuredOutput -- 需要 StructuredOutputConfig
22. Feature/Pipeline 系统 -- 大型子系统，jasmine 用 Tracing 替代
