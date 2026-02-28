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
├── agent-graph/      # 新建：图执行引擎、策略、节点
├── agent-a2a/        # 新建：Agent-to-Agent 通信协议
├── agent-mcp/        # 新建：MCP 协议客户端
├── agent-planner/    # 新建：规划算法（GOAP、LLM Planner）
├── agent-observe/    # 新建：trace + snapshot + event + feature 合并
├── agent-runtime/    # 已有，不动
└── agent-dex/        # 已有，不动
```

### 为什么 trace/snapshot/event/feature 合并为 agent-observe

- trace、snapshot、event、feature 都是 Agent 执行过程的可观测性/生命周期管理
- 它们之间有交叉引用（graph 引用 trace 和 feature）
- 单独拆太碎（event 只有 4 个文件），合并后约 29 个文件，规模合理
- 统一的包名 `com.lhzkml.jasmine.core.agent.observe`，子包保持 `trace/`、`snapshot/`、`event/`、`feature/`

## 模块依赖关系

```
agent-tools    → prompt-llm（保持不变）
agent-observe  → prompt-model（trace/snapshot 需要 ChatMessage 等）
agent-graph    → agent-tools, agent-observe（graph 引用 Tool/ToolRegistry + trace + feature）
agent-planner  → agent-graph（planner 引用 AgentGraphContext）
agent-mcp      → agent-tools（mcp 引用 Tool 接口）
agent-a2a      → agent-tools（a2a 引用 ToolRegistry）
agent-runtime  → agent-graph, agent-planner, agent-mcp, agent-observe, config-manager
agent-dex      → agent-tools（保持不变）
```

## 各模块内容

### agent-tools（保留，精简后）

只保留工具核心和具体工具实现：

```
agent-tools/
├── Tool.kt                    # Tool 接口
├── ToolExecutor.kt            # Agent Loop（需移除 trace 依赖，改为接口回调）
├── ToolRegistry.kt            # 工具注册表
├── ShellPolicy.kt / ShellPolicyConfig.kt
├── ReadFileTool.kt, WriteFileTool.kt, EditFileTool.kt ...
├── ExecuteShellCommandTool.kt, FetchUrlTool.kt, WebSearchTool.kt ...
├── CalculatorTool.kt, GetCurrentTimeTool.kt ...
└── AskUserTool.kt, SayToUserTool.kt, AttemptCompletionTool.kt
```

ToolExecutor 当前直接 import trace 包。重构后改为通过 `AgentEventListener` 接口回调（已有），trace 的具体实现由上层注入。

### agent-observe（新建）

```
agent-observe/
├── trace/
│   ├── TraceEvent.kt, TraceEventCategory.kt
│   ├── TraceWriter.kt（接口）
│   ├── FileTraceWriter.kt, LogTraceWriter.kt, CallbackTraceWriter.kt
│   ├── TraceMessageFormat.kt
│   └── Tracing.kt
├── snapshot/
│   ├── AgentCheckpoint.kt, AgentCheckpointPredicateFilter.kt
│   ├── Persistence.kt, PersistenceUtils.kt
│   ├── PersistenceStorageProvider.kt
│   └── RollbackToolRegistry.kt, RollbackStrategy.kt
├── event/
│   ├── EventHandler.kt, EventCategory.kt
│   ├── EventContexts.kt, EventHandlerConfig.kt
└── feature/
    ├── AgentFeature.kt, FeatureKey.kt
    ├── AgentLifecycleEventContext.kt, AgentLifecycleEventType.kt
    ├── config/, handler/, message/, pipeline/
```

### agent-graph（新建）

```
agent-graph/
├── GraphAgent.kt, FunctionalAgent.kt
├── AgentNode.kt, AgentEdge.kt, AgentState.kt
├── AgentStrategy.kt, FunctionalStrategy.kt
├── AgentGraphContext.kt, AgentEnvironment.kt
├── AgentService.kt, AgentSubgraph.kt
├── PredefinedNodes.kt, PredefinedStrategies.kt
├── GraphStrategyBuilder.kt, ExecutionPointNode.kt
├── ToolCalls.kt, ToolResultKind.kt, ToolSelectionStrategy.kt
├── AgentAsTool.kt, AgentStorage.kt, AgentExecutionInfo.kt
└── FunctionalContextExt.kt
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

### agent-a2a（新建）

```
agent-a2a/
├── A2AExceptions.kt
├── client/
├── server/
├── model/
├── transport/
└── utils/
```

## ToolExecutor 解耦 trace 的方式

当前 ToolExecutor 直接 import `Tracing`、`TraceEvent`、`TraceError`。

解耦方案：在 agent-tools 中定义一个 `AgentTracer` 接口，ToolExecutor 依赖这个接口。`Tracing` 类在 agent-observe 中实现这个接口。

```kotlin
// agent-tools 中定义
interface AgentTracer {
    fun newRunId(): String
    fun newEventId(): String
    suspend fun emit(event: Any)
}

// agent-observe 中 Tracing 实现 AgentTracer
class Tracing(...) : AgentTracer { ... }
```

这样 agent-tools 不再依赖 agent-observe。

## config-manager 解耦

当前 `ConfigRepository` 接口 import 了 `TraceEventCategory`、`EventCategory`、`RollbackStrategy`、`ShellPolicy`。

这些枚举/类分布在 agent-tools 和即将拆出的 agent-observe 中。解耦方案：

- `ShellPolicy` 留在 agent-tools，config-manager 继续依赖 agent-tools
- `TraceEventCategory`、`EventCategory`、`RollbackStrategy` 移到 agent-observe 后，config-manager 增加对 agent-observe 的依赖

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
| `c.l.j.core.agent.tools.feature.*` | `c.l.j.core.agent.observe.feature.*` |

## 执行顺序

1. 新建 agent-observe 模块，移动 trace/snapshot/event/feature，更新包名和 import
2. 在 agent-tools 中定义 `AgentTracer` 接口，ToolExecutor 改用接口
3. 新建 agent-graph 模块，移动 graph/，更新包名和 import
4. 新建 agent-planner 模块，移动 planner/，更新包名和 import
5. 新建 agent-mcp 模块，移动 mcp/，更新包名和 import
6. 新建 agent-a2a 模块，移动 a2a/，更新包名和 import
7. 更新 agent-runtime、config-manager、app 的依赖和 import
8. 更新 settings.gradle.kts
9. 运行 `.\gradlew.bat test` 确认全部通过
10. 运行 `.\gradlew.bat assembleDebug` 构建 APK

## 注意事项

- 每完成一个模块的移动后立即编译验证，不要等全部移完再编译
- 移动文件后全局搜索旧包名，确保没有遗漏的 import
- 测试文件跟随源码一起移动到新模块的 `src/test/` 下
- `RollbackStrategy` 枚举目前在 snapshot 包中，移到 agent-observe 后，所有引用它的地方（ConfigRepository、SharedPreferencesConfigRepository、ProviderManager 等）都要更新 import
- `snapshot/Persistence.kt` 引用了 `graph/AgentGraphContext`，移动后 agent-observe 需要依赖 agent-graph，或者将 `AgentStorageKey` 抽到更底层的模块。评估后决定：Persistence 中对 AgentGraphContext 的引用可以改为泛型接口，避免 agent-observe 反向依赖 agent-graph
