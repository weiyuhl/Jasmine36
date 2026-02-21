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

## 二、未移植 -- 预定义节点

来源: `koog dsl/extension/AIAgentNodes.kt`
目标: `jasmine PredefinedNodes.kt`

jasmine 已有 10+8 个节点（原始 10 个 + Task 26 移植 8 个），koog 共有约 24 个节点。

已移植的节点（Task 26 新增）:

| 节点名 | 状态 | 功能说明 |
|---|---|---|
| nodeLLMRequestOnlyCallingTools | [已完成] | 追加用户消息，强制LLM只能调用工具(ToolChoice.Required) |
| nodeLLMRequestMultiple | [已完成] | 追加用户消息，获取多个LLM响应 |
| nodeLLMRequestMultipleOnlyCallingTools | [已完成] | 多响应 + 只能调用工具 |
| nodeLLMRequestForceOneTool | [已完成] | 强制LLM使用指定工具(ToolChoice.Named) |
| nodeLLMCompressHistory | [已完成] | 历史压缩节点，压缩后透传输入 |
| nodeLLMRequestStructured | [已完成] | 请求LLM返回结构化JSON输出 |
| nodeExecuteMultipleToolsAndSendResults | [已完成] | 执行多工具并发送结果给LLM |
| nodeLLMSendToolResultOnlyCallingTools | [已完成] | 发送工具结果 + 强制只能调用工具 |
| nodeLLMSendMultipleToolResultsMultiple | [已完成] | 发送多工具结果，获取多响应 |
| nodeLLMSendMultipleToolResultsOnlyCallingTools | [已完成] | 发送多工具结果 + 强制只能调用工具 |

仍未移植（低优先级 / 架构差异大）:

| 节点名 | koog 函数签名 | 功能说明 |
|---|---|---|
| nodeLLMModerateMessage | `(Message) -> ModeratedMessage` | 内容审核节点，需要 moderate() API |
| nodeLLMRequestStreaming (Flow) | `(String) -> Flow<StreamFrame>` | Flow-based流式请求(koog用Flow，jasmine用回调) |
| nodeLLMRequestStreamingAndSendResults | `(T) -> List<Message.Response>` | 流式请求并自动收集结果更新prompt |
| nodeExecuteSingleTool | `(ToolArg) -> SafeTool.Result<TResult>` | 直接调用指定工具(依赖SafeTool类型系统) |
| nodeSetStructuredOutput | `(TInput) -> TInput` | 设置结构化输出schema(需要StructuredOutputConfig) |
| nodeDoNothing | `(T) -> T` | 透传节点 |
| nodeUpdatePrompt | `(T) -> T` | 修改prompt后透传 |
| nodeLLMSendMessageOnlyCallingTools | `(String) -> Message.Response` | 同nodeLLMRequestOnlyCallingTools，koog别名 |
| nodeLLMSendMessageForceOneTool | `(String) -> Message.Response` | 同nodeLLMRequestForceOneTool，koog别名 |

### 移植难度评估（仍未移植的项目）

- nodeLLMModerateMessage: 需要 moderate() API，jasmine 没有，需要新增
- nodeLLMRequestStreaming (Flow): koog 用 Kotlin Flow，jasmine 用回调式。架构差异较大
- nodeExecuteSingleTool: 依赖 SafeTool 类型系统，jasmine 没有。需要适配
- nodeSetStructuredOutput: 需要 StructuredOutputConfig，jasmine 没有完整的结构化输出配置系统
- nodeDoNothing / nodeUpdatePrompt: 简单，可随时移植

---

## 三、未移植 -- 边条件

来源: `koog dsl/extension/AIAgentEdges.kt`
目标: `jasmine AgentEdge.kt`

jasmine 已有 5 个边条件，koog 共有约 17 个。以下 12 个缺失:

| 边条件 | koog 签名 | 功能说明 |
|---|---|---|
| onIsInstance(KClass) | `EdgeBuilder -> EdgeBuilder` | 按类型过滤输出(类似 is 检查) |
| onSuccessful(condition) | `EdgeBuilder<SafeTool.Result> -> EdgeBuilder<Success>` | SafeTool成功结果过滤 |
| onFailure(condition) | `EdgeBuilder<SafeTool.Result> -> EdgeBuilder<Failure>` | SafeTool失败结果过滤 |
| onToolCall(tool: Tool) | `EdgeBuilder -> EdgeBuilder<ToolCall>` | 按指定工具名过滤工具调用 |
| onToolCall(tool, argsCondition) | `EdgeBuilder -> EdgeBuilder<ToolCall>` | 按工具名+参数条件过滤 |
| onToolNotCalled(tool) | `EdgeBuilder -> EdgeBuilder<ToolCall>` | 排除指定工具的调用 |
| onToolResult(tool, condition) | `EdgeBuilder -> EdgeBuilder<ReceivedToolResult>` | 按工具结果过滤 |
| onMultipleToolCalls | `EdgeBuilder<List<Response>> -> EdgeBuilder<List<ToolCall>>` | 多工具调用列表过滤 |
| onMultipleToolResults | `EdgeBuilder -> EdgeBuilder<List<ReceivedToolResult>>` | 多工具结果列表过滤 |
| onMultipleAssistantMessages | `EdgeBuilder<List<Response>> -> EdgeBuilder<List<Assistant>>` | 多助手消息过滤 |
| onMultipleReasoningMessages | `EdgeBuilder<List<Response>> -> EdgeBuilder<List<Reasoning>>` | 多推理消息过滤 |
| onAssistantMessageWithMedia | `EdgeBuilder -> EdgeBuilder<List<Attachment>>` | 带媒体附件的助手消息过滤 |
| onReasoningMessage | `EdgeBuilder -> EdgeBuilder<Reasoning>` | 推理消息过滤 |

### 移植难度评估

- onIsInstance: 简单，纯类型检查
- onMultipleToolCalls / onMultipleAssistantMessages: 需要 jasmine 支持 List<Message.Response> 类型的节点输出，目前 jasmine 用 ChatResult 而非 Message 子类型
- onSuccessful / onFailure: 依赖 SafeTool.Result 类型，jasmine 没有
- onToolCall(tool) / onToolNotCalled: 需要 Tool 对象引用，jasmine 的 Tool 是字符串名称匹配，可适配
- onAssistantMessageWithMedia / onReasoningMessage: 依赖 koog 的 Message 类型层次(Assistant, Reasoning, ContentPart.Attachment)，jasmine 用扁平的 ChatMessage/ChatResult

---

## 四、未移植 -- FunctionalContext 扩展函数

来源: `koog dsl/extension/AIAgentFunctionalContextExt.kt`
目标: 无对应文件

koog 为 FunctionalContext 提供了约 20 个便捷扩展函数，jasmine 的 FunctionalStrategy 直接操作 AgentGraphContext.session/environment，没有这些便捷封装:

| 扩展函数 | 功能 |
|---|---|
| requestLLM(message, allowToolCalls) | 追加用户消息并请求LLM |
| onAssistantMessage(response, action) | 如果是助手消息则执行action |
| containsToolCalls() | 检查响应列表是否包含工具调用 |
| asAssistantMessageOrNull() | 安全转换为助手消息 |
| asAssistantMessage() | 强制转换为助手消息 |
| onMultipleToolCalls(response, action) | 如果有多个工具调用则执行action |
| extractToolCalls(response) | 从响应列表提取工具调用 |
| onMultipleAssistantMessages(response, action) | 如果有多个助手消息则执行action |
| latestTokenUsage() | 获取最新token用量 |
| requestLLMStructured(message, examples) | 请求结构化输出 |
| requestLLMStreaming(message) | 流式请求 |
| requestLLMMultiple(message) | 多响应请求 |
| requestLLMOnlyCallingTools(message) | 只能调用工具的请求 |
| requestLLMForceOneTool(message, tool) | 强制使用指定工具 |
| executeTool(toolCall) | 执行工具 |
| executeMultipleTools(toolCalls, parallel) | 执行多个工具 |
| sendToolResult(toolResult) | 发送工具结果并请求LLM |
| sendMultipleToolResults(results) | 发送多个工具结果 |
| executeSingleTool(tool, args) | 直接调用指定工具 |
| compressHistory(strategy) | 压缩历史 |

### 移植难度评估

大部分是对 session/environment 的简单封装，移植难度低。但部分函数(requestLLMMultiple, requestLLMOnlyCallingTools, requestLLMForceOneTool)需要 LLMSession 先支持对应的请求模式。

---

## 五、未移植 -- ToolSelectionStrategy

来源: `koog agent/entity/AIAgentSubgraph.kt` 中的 sealed interface
目标: `jasmine AgentSubgraph.kt`

| 策略 | 功能 |
|---|---|
| ALL | 使用所有可用工具(默认) |
| NONE | 不使用任何工具 |
| Tools(list) | 使用指定的工具列表 |
| AutoSelectForTask(description) | LLM根据子任务描述自动选择相关工具 |

jasmine 的 AgentSubgraph 没有工具过滤能力，所有子图共享同一个 toolRegistry。

### 移植难度评估

- ALL/NONE/Tools: 简单，在子图执行前过滤 toolRegistry 即可
- AutoSelectForTask: 需要额外的 LLM 调用来选择工具，需要结构化输出支持

---

## 六、未移植 -- ExecutionPointNode (强制执行点)

来源: `koog agent/entity/ExecutionPointNode.kt`
目标: 无对应

| 接口方法 | 功能 |
|---|---|
| getExecutionPoint() | 获取当前强制执行点 |
| resetExecutionPoint() | 重置执行点 |
| enforceExecutionPoint(node, input) | 设置强制执行点(跳转到指定节点) |

用于 checkpoint/rollback 场景: 子图执行中可以跳转到任意节点重新执行。

### 移植难度评估

中等。需要修改 AgentSubgraph 的执行循环，在每次迭代开始时检查是否有强制执行点。

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

### 高优先级 (实用性强，移植难度低)

1. FunctionalContext 扩展函数 -- 大幅提升 functionalStrategy 的易用性 [已完成 863b411]
2. nodeLLMRequestForceOneTool -- LLMSession 已有 setToolChoiceNamed()
3. nodeLLMRequestOnlyCallingTools -- LLMSession 已有 setToolChoiceRequired()
4. nodeLLMRequestStructured -- LLMSession 已有 requestLLMStructured()
5. nodeLLMCompressHistory -- HistoryCompressionStrategy 已完整
6. ToolSelectionStrategy (ALL/NONE/Tools) -- 简单过滤 [已完成 863b411]

### 中优先级 (有价值，需要一定工作量)

7. onIsInstance 边条件 -- 通用类型过滤 [已完成]
8. onToolCall(tool) / onToolNotCalled(tool) -- 按工具名过滤 [已完成]
9. onMultipleToolCalls / onMultipleAssistantMessages -- 需要支持 List 类型节点 [已完成]
10. nodeLLMRequestMultiple -- 需要 LLMSession 新增 requestLLMMultiple() [已完成]
11. ExecutionPointNode -- checkpoint/rollback [已完成]
12. ToolSelectionStrategy.AutoSelectForTask -- 需要结构化输出 [已完成]

### 低优先级 (依赖架构变更或使用场景有限)

13. SafeTool 类型系统 -- 需要重构 Tool 基类
14. nodeExecuteSingleTool -- 依赖 SafeTool
15. onSuccessful / onFailure -- 依赖 SafeTool
16. nodeLLMModerateMessage -- 需要 moderate() API
17. Flow-based streaming nodes -- 架构差异大
18. Feature/Pipeline 系统 -- 大型子系统，jasmine 用 Tracing 替代
