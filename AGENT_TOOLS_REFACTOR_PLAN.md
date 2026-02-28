# agent-tools 模块拆分重构方案

## 问题

`agent-tools` 模块塞了 8 个不同功能域，100+ 个文件。违反单一职责原则。

```
agent-tools/（当前）
├── Tool.kt, ToolExecutor.kt, ToolRegistry.kt   # 核心
├── 20+ 具体工具实现（ReadFileTool 等）            # 核心
├── graph/     (22 文件) — Agent 图执行引擎
├── a2a/       (11 文件) — Agent-to-Agent 通信
├── mcp/       (6 文件)  — MCP 协议客户端
├── planner/   (5 文件)  — 规划算法
├── feature/   (11 文件) — Feature 管理框架
├── trace/     (8 文件)  — 执行追踪
├── snapshot/  (6 文件)  — 检查点/回滚
└── event/     (4 文件)  — 事件处理
```

## 拆分方案

拆为 6 个独立模块：

```
jasmine-core/agent/
├── agent-tools/      # 保留：Tool 接口、ToolExecutor、ToolRegistry、具体工具
├── agent-graph/      # 新建：图执行引擎、策略、节点、feature/pipeline
├── agent-a2a/        # 新建：Agent-to-Agent 通信协议
├── agent-mcp/        # 新建：MCP 协议客户端
├── agent-planner/    # 新建：规划算法（GOAP、LLM Planner）
├── agent-observe/    # 新建：trace + snapshot + event
└── agent-runtime/    # 已有，不动
```

## feature 归属问题

经过依赖分析，feature 包（AgentFeature、Pipeline 等）和 graph 包之间存在双向强耦合：
- feature/pipeline 的几乎所有 handler 都 import `AgentGraphContext`
- graph 的 `GraphAgent`、`FunctionalAgent`、`AgentSubgraph`、`AgentGraphContext` 都 import feature/pipeline
- feature/handler/ToolCallEventHandler 还引用了 `trace.TraceError`

因此 feature 不适合放在 agent-observe 中（会导致 observe ↔ graph 循环依赖）。
**feature 跟随 graph 一起放入 agent-graph 模块。**

## agent-observe 只包含 trace + snapshot + event

- trace：执行追踪，纯数据记录
- snapshot：检查点/回滚
- event：事件处理器
- 这三者之间没有交叉引用，且都是独立的可观测性功能
- 合并后约 18 个文件，规模合理

## snapshot/Persistence 对 graph 的依赖处理

`Persistence.kt` 引用了 `AgentGraphContext` 和 `AgentStorageKey`，具体用途：
- `restoreFromCheckpoint()` 需要操作 `context.session` 和 `context.storage`
- `KEY_RESTORED_NODE` / `KEY_RESTORED_INPUT` 使用 `AgentStorageKey<String>`

解耦方案：将 Persistence 中依赖 AgentGraphContext 的方法（`restoreFromCheckpoint`、`rollbackToCheckpoint`、`rollbackToLatestCheckpoint`、`setExecutionPoint`）抽取为扩展函数，放在 agent-graph 模块中。Persistence 本体只保留纯检查点 CRUD 操作（`createCheckpoint`、`getCheckpoints`、`deleteCheckpoint` 等），不依赖 graph。

```kotlin
// agent-observe 中的 Persistence — 只做检查点存储
class Persistence(...) {
    suspend fun createCheckpoint(...): AgentCheckpoint
    suspend fun getLatestCheckpoint(agentId: String): AgentCheckpoint?
    suspend fun getCheckpoints(agentId: String): List<AgentCheckpoint>
    suspend fun markCompleted(agentId: String)
    suspend fun clearCheckpoints(agentId: String)
    // 不再包含 restoreFromCheckpoint / rollbackToCheckpoint
}

// agent-graph 中的扩展函数 — 需要 AgentGraphContext 的操作
suspend fun Persistence.restoreFromCheckpoint(context: AgentGraphContext, checkpoint: AgentCheckpoint) { ... }
suspend fun Persistence.rollbackToCheckpoint(checkpointId: String, context: AgentGraphContext): AgentCheckpoint? { ... }
```

`AgentStorageKey` 的 `KEY_RESTORED_NODE` / `KEY_RESTORED_INPUT` 常量也移到 agent-graph 的扩展文件中。

## 模块依赖关系

```
agent-tools    → prompt-llm（保持不变，移除 trace 依赖）
agent-observe  → prompt-model（snapshot 需要 ChatMessage）
agent-graph    → agent-tools, agent-observe, prompt-llm（graph 引用 Tool/ToolRegistry + trace + LLMSession）
agent-planner  → agent-graph（planner 引用 AgentGraphContext）
agent-mcp      → agent-tools, ktor（mcp 引用 Tool 接口 + HTTP 客户端）
agent-a2a      → ktor, kotlinx-serialization（a2a 是独立协议，不依赖其他 agent 模块）
agent-runtime  → agent-graph, agent-planner, agent-mcp, agent-observe, config-manager
config-manager → agent-tools, agent-observe, prompt-executor（需要 ShellPolicy + TraceEventCategory + EventCategory + RollbackStrategy）
```

## 各模块内容

### agent-tools（保留，精简后）

```
agent-tools/
├── Tool.kt, ToolExecutor.kt, ToolRegistry.kt
├── AgentTracer.kt              # 新增：追踪接口（替代直接依赖 Tracing）
├── AgentEventListener.kt       # 已有：事件回调接口
├── ShellPolicy.kt, ShellPolicyConfig.kt
├── ReadFileTool.kt, WriteFileTool.kt, EditFileTool.kt, AppendFileTool.kt
├── CreateFileTool.kt, DeleteFileTool.kt, CopyFileTool.kt, MoveFileTool.kt
├── RenameFileTool.kt, InsertContentTool.kt, ReplaceInFileTool.kt
├── ListDirectoryTool.kt, CreateDirectoryTool.kt, FindFilesTool.kt
├── FileInfoTool.kt, CompressFilesTool.kt, RegexSearchTool.kt
├── ExecuteShellCommandTool.kt, GetCurrentTimeTool.kt
├── FetchUrlTool.kt, WebSearchTool.kt
├── CalculatorTool.kt, ExitTool.kt
├── AskUserTool.kt, SayToUserTool.kt, AttemptCompletionTool.kt
```

### agent-observe（新建）

```
agent-observe/
├── trace/
│   ├── TraceEvent.kt, TraceEventCategory.kt, TraceError.kt
│   ├── TraceWriter.kt（接口）
│   ├── FileTraceWriter.kt, LogTraceWriter.kt, CallbackTraceWriter.kt
│   ├── TraceMessageFormat.kt
│   └── Tracing.kt（实现 AgentTracer 接口）
├── snapshot/
│   ├── AgentCheckpoint.kt, AgentCheckpointPredicateFilter.kt
│   ├── Persistence.kt（纯 CRUD，不依赖 graph）
│   ├── PersistenceUtils.kt, PersistenceStorageProvider.kt
│   ├── InMemoryPersistenceStorageProvider.kt, FilePersistenceStorageProvider.kt
│   ├── NoPersistenceStorageProvider.kt
│   ├── RollbackToolRegistry.kt, RollbackStrategy.kt
└── event/
    ├── EventHandler.kt, EventCategory.kt
    ├── EventContexts.kt, EventHandlerConfig.kt
```

### agent-graph（新建）

```
agent-graph/
├── graph/
│   ├── GraphAgent.kt, FunctionalAgent.kt
│   ├── AgentNode.kt, AgentEdge.kt, AgentState.kt
│   ├── AgentStrategy.kt, FunctionalStrategy.kt
│   ├── AgentGraphContext.kt, AgentEnvironment.kt
│   ├── AgentService.kt, AgentSubgraph.kt
│   ├── AgentStorage.kt, AgentStorageKey.kt, AgentExecutionInfo.kt
│   ├── PredefinedNodes.kt, PredefinedStrategies.kt
│   ├── GraphStrategyBuilder.kt, ExecutionPointNode.kt
│   ├── ToolCalls.kt, ToolResultKind.kt, ToolSelectionStrategy.kt
│   ├── AgentAsTool.kt, FunctionalContextExt.kt
│   └── PersistenceGraphExt.kt   # 新增：Persistence 的 graph 扩展函数
├── feature/
│   ├── AgentFeature.kt, FeatureKey.kt
│   ├── AgentLifecycleEventContext.kt, AgentLifecycleEventType.kt
│   ├── config/, handler/, message/, pipeline/
```

### agent-planner（新建）

```
agent-planner/
├── AgentPlanner.kt（接口）
├── GOAPPlanner.kt, GOAPPlannerBuilder.kt
├── SimpleLLMPlanner.kt
└── SimpleLLMWithCriticPlanner.kt
```

### agent-mcp（新建）

```
agent-mcp/
├── McpClient.kt（接口）
├── HttpMcpClient.kt, SseMcpClient.kt
├── McpTool.kt, McpToolAdapter.kt
├── McpToolDefinitionParser.kt
└── McpToolRegistryProvider.kt
```

需要的依赖：agent-tools（Tool 接口）、ktor-client-okhttp、ktor-client-content-negotiation、ktor-serialization-kotlinx-json。这些 ktor 依赖从 agent-tools 移到 agent-mcp。

### agent-a2a（新建）

```
agent-a2a/
├── A2AExceptions.kt
├── client/   — A2AClient, AgentCardResolver
├── server/   — A2AServer, AgentExecutor, Session, SessionManager, ...
├── model/    — 数据模型
├── transport/ — 传输层
└── utils/    — KeyedMutex, RWLock 等
```

a2a 是完全独立的协议实现，只依赖 ktor 和 kotlinx-serialization，不依赖其他 agent 模块。

## ToolExecutor 解耦 trace 的方式

当前 ToolExecutor 直接 import `Tracing`、`TraceEvent`、`TraceError`。

解耦方案：在 agent-tools 中定义 `AgentTracer` 接口：

```kotlin
// agent-tools 中定义
interface AgentTracer {
    fun newRunId(): String
    fun newEventId(): String
    suspend fun emit(event: Any)
}
```

ToolExecutor 的构造参数 `tracing: Tracing?` 改为 `tracer: AgentTracer?`。
agent-observe 中的 `Tracing` 类实现 `AgentTracer` 接口。

ToolExecutor 中所有 `TraceEvent.XXX(...)` 的构造改为通过 tracer 的工厂方法或直接传 Map/data class。
但这样改动量很大。**更简单的方案**：ToolExecutor 保持对 trace 的依赖，agent-tools 依赖 agent-observe。

权衡后的决定：**agent-tools 增加对 agent-observe 的 api 依赖**。理由：
- ToolExecutor 是 Agent Loop 的核心，trace 是它的内在需求
- 强行解耦会引入大量抽象层，得不偿失
- agent-observe 是纯数据/接口模块，不会引入重量级依赖

最终依赖：
```
agent-tools → prompt-llm, agent-observe
```

## config-manager 依赖变更

当前 config-manager 依赖 agent-tools（获取 ShellPolicy、RollbackStrategy、TraceEventCategory、EventCategory）。

拆分后：
- `ShellPolicy` 留在 agent-tools
- `TraceEventCategory`、`EventCategory`、`RollbackStrategy` 移到 agent-observe

config-manager 需要同时依赖 agent-tools 和 agent-observe：
```kotlin
api(project(":jasmine-core:agent:agent-tools"))
api(project(":jasmine-core:agent:agent-observe"))
```

## agent-tools 的 ktor 依赖处理

当前 agent-tools 的 build.gradle.kts 包含 ktor 依赖（okhttp、content-negotiation、serialization）。

分析实际使用：
- `FetchUrlTool.kt`、`WebSearchTool.kt` — 使用 ktor HTTP 客户端
- `mcp/HttpMcpClient.kt`、`mcp/SseMcpClient.kt` — 使用 ktor HTTP 客户端
- `a2a/` — 使用 ktor 和 okhttp

拆分后：
- agent-tools 保留 ktor 依赖（FetchUrlTool、WebSearchTool 需要）
- agent-mcp 也需要 ktor 依赖
- agent-a2a 也需要 ktor 依赖

## app 模块依赖变更

当前 app 直接 import 了以下被拆出的包：
- `graph.*` → 需要依赖 agent-graph
- `planner.*` → 需要依赖 agent-planner
- `mcp.*` → 需要依赖 agent-mcp
- `trace.*` → 需要依赖 agent-observe
- `snapshot.*` → 需要依赖 agent-observe
- `event.*` → 需要依赖 agent-observe

app/build.gradle.kts 需要新增：
```kotlin
implementation(project(":jasmine-core:agent:agent-graph"))
implementation(project(":jasmine-core:agent:agent-planner"))
implementation(project(":jasmine-core:agent:agent-mcp"))
implementation(project(":jasmine-core:agent:agent-observe"))
```

## settings.gradle.kts 变更

```kotlin
// 新增
include(":jasmine-core:agent:agent-graph")
include(":jasmine-core:agent:agent-planner")
include(":jasmine-core:agent:agent-mcp")
include(":jasmine-core:agent:agent-a2a")
include(":jasmine-core:agent:agent-observe")
```

## 包名映射

| 旧包名 | 新包名 |
|--------|--------|
| `c.l.j.core.agent.tools.graph.*` | `c.l.j.core.agent.graph.*` |
| `c.l.j.core.agent.tools.planner.*` | `c.l.j.core.agent.planner.*` |
| `c.l.j.core.agent.tools.mcp.*` | `c.l.j.core.agent.mcp.*` |
| `c.l.j.core.agent.tools.a2a.*` | `c.l.j.core.agent.a2a.*` |
| `c.l.j.core.agent.tools.trace.*` | `c.l.j.core.agent.observe.trace.*` |
| `c.l.j.core.agent.tools.snapshot.*` | `c.l.j.core.agent.observe.snapshot.*` |
| `c.l.j.core.agent.tools.event.*` | `c.l.j.core.agent.observe.event.*` |
| `c.l.j.core.agent.tools.feature.*` | `c.l.j.core.agent.graph.feature.*` |

## 受影响的外部文件（需要更新 import）

### trace 包的外部引用
- `config-manager/ConfigRepository.kt` — TraceEventCategory
- `agent-runtime/AgentRuntimeBuilder.kt` — LogTraceWriter, FileTraceWriter, Tracing
- `app/MainActivity.kt` — Tracing
- `app/ProviderManager.kt` — TraceEventCategory
- `app/SharedPreferencesConfigRepository.kt` — TraceEventCategory
- `app/TraceConfigActivity.kt` — TraceEventCategory
- `app/test/StubConfigRepository.kt` — TraceEventCategory
- `app/test/ProviderManagerTest.kt` — TraceEventCategory

### snapshot 包的外部引用
- `config-manager/ConfigRepository.kt` — RollbackStrategy
- `agent-runtime/AgentRuntimeBuilder.kt` — InMemoryPersistenceStorageProvider, FilePersistenceStorageProvider, Persistence
- `agent-runtime/CheckpointService.kt` — AgentCheckpoint, FilePersistenceStorageProvider, Persistence
- `app/MainActivity.kt` — Persistence, AgentCheckpoint
- `app/ProviderManager.kt` — RollbackStrategy
- `app/SharedPreferencesConfigRepository.kt` — RollbackStrategy
- `app/SnapshotConfigActivity.kt` — RollbackStrategy
- `app/CheckpointManagerActivity.kt` — AgentCheckpoint
- `app/CheckpointDetailActivity.kt` — AgentCheckpoint
- `app/test/StubConfigRepository.kt` — RollbackStrategy
- `app/test/ProviderManagerTest.kt` — RollbackStrategy

### event 包的外部引用
- `config-manager/ConfigRepository.kt` — EventCategory
- `agent-runtime/AgentRuntimeBuilder.kt` — EventCategory, EventHandler
- `app/MainActivity.kt` — EventHandler, EventCategory
- `app/ProviderManager.kt` — EventCategory
- `app/SharedPreferencesConfigRepository.kt` — EventCategory
- `app/EventHandlerConfigActivity.kt` — EventCategory
- `app/test/StubConfigRepository.kt` — EventCategory
- `app/test/ProviderManagerTest.kt` — EventCategory

### graph 包的外部引用
- `app/MainActivity.kt` — AgentGraphContext, GenericAgentEnvironment, GraphAgent, PredefinedStrategies, ToolCalls, ToolSelectionStrategy

### planner 包的外部引用
- `app/MainActivity.kt` — SimpleLLMPlanner, SimpleLLMWithCriticPlanner

### mcp 包的外部引用
- `agent-runtime/McpConnectionManager.kt` — HttpMcpClient, McpClient, McpToolAdapter, McpToolDefinition, McpToolRegistryProvider, SseMcpClient
- `app/McpServerActivity.kt` — HttpMcpClient, McpToolDefinition, SseMcpClient
- `app/McpServerEditActivity.kt` — HttpMcpClient, SseMcpClient

### feature 包的外部引用
- 无外部引用（只被 graph 内部引用）

## 测试文件迁移

| 当前位置 | 迁移到 |
|---------|--------|
| `agent-tools/test/.../graph/GraphAgentTest.kt` | `agent-graph/test/.../graph/GraphAgentTest.kt` |
| `agent-tools/test/.../graph/AgentExecutionInfoTest.kt` | `agent-graph/test/.../graph/AgentExecutionInfoTest.kt` |
| `agent-tools/test/.../trace/TracingTest.kt` | `agent-observe/test/.../observe/trace/TracingTest.kt` |
| `agent-tools/test/.../ToolExecutorTest.kt` | 留在 agent-tools |
| `agent-tools/test/.../CalculatorToolTest.kt` 等 | 留在 agent-tools |

注意：`GraphAgentTest.kt` 引用了 `trace.CallbackTraceWriter`、`trace.TraceEvent`、`trace.Tracing`，迁移后需要更新 import 为 `agent-observe` 的包名。

## 执行顺序

1. 新建 agent-observe 模块（build.gradle.kts + 目录结构），移动 trace/snapshot/event，更新所有包名声明和 import
2. agent-tools 增加对 agent-observe 的依赖，ToolExecutor 的 import 改为新包名
3. 新建 agent-graph 模块，移动 graph/ 和 feature/，创建 PersistenceGraphExt.kt，更新包名和 import
4. 新建 agent-planner 模块，移动 planner/，更新包名和 import
5. 新建 agent-mcp 模块，移动 mcp/，更新包名和 import
6. 新建 agent-a2a 模块，移动 a2a/，更新包名和 import
7. 更新 config-manager 依赖（增加 agent-observe）
8. 更新 agent-runtime 依赖（增加 agent-graph、agent-planner、agent-mcp、agent-observe）
9. 更新 app 依赖（增加 agent-graph、agent-planner、agent-mcp、agent-observe）
10. 更新 settings.gradle.kts
11. 全局搜索旧包名，确认无遗漏
12. 运行 `.\gradlew.bat test`
13. 运行 `.\gradlew.bat assembleDebug`

## 注意事项

- 每完成一个模块的移动后立即编译验证，不要等全部移完再编译
- 移动文件后全局搜索旧包名（`agent.tools.graph`、`agent.tools.trace` 等），确保没有遗漏的 import
- 测试文件跟随源码一起移动到新模块的 `src/test/` 下
- 每个新模块的 `build.gradle.kts` 需要配置 `namespace`、`compileSdk`、`minSdk`、`jvmTarget` 等，保持与现有模块一致
- `TraceError` 类当前在 `trace/TraceEvent.kt` 中定义，被 `feature/handler/ToolCallEventHandler.kt` 和 `graph/AgentStrategy.kt` 引用。移到 agent-observe 后，agent-graph 通过依赖 agent-observe 访问
- `RollbackStrategy` 枚举当前定义在 `snapshot/AgentCheckpoint.kt` 文件中（不是独立文件），移动时注意一起带走
- `PersistenceStorageProvider` 有三个实现类（InMemory、File、No），都在 snapshot 包中，需要一起移动
- `kotlinOptions { freeCompilerArgs += listOf("-Xnested-type-aliases") }` 在 app 的 build.gradle.kts 中，如果新模块也需要 typealias 嵌套，需要加上
- agent-mcp 的 `McpToolAdapter` 和 `McpToolRegistryProvider` 引用了 `ToolRegistry`（在 agent-tools 中），所以 agent-mcp 必须依赖 agent-tools
- agent-a2a 不引用任何其他 agent 子模块的类，是完全独立的
