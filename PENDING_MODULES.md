# jasmine-core 模块集成状态

以下是 `jasmine-core` 中所有模块在应用层（`app`）的集成状态。

## 已集成模块

### 1. MCP（Model Context Protocol）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../mcp/`
- 说明：MCP 工具注册提供者，支持从 MCP 服务器动态加载工具。
- 集成方式：支持配置多个 MCP 服务器（Streamable HTTP / SSE），Agent 模式下自动预连接并加载远程工具。
- 配置入口：设置 → MCP 工具（McpServerActivity）

### 2. Graph Agent（图执行引擎）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../graph/`
- 说明：基于节点/边/子图/策略的图执行引擎，参考 koog 的 singleRunStrategy。
- 集成方式：作为 Agent 执行策略之一（SINGLE_RUN_GRAPH），在 MainActivity 中通过 GraphAgent + PredefinedStrategies.singleRunStreamStrategy() 执行流式 Agent Loop。
- 配置入口：设置 → Agent 策略（AgentStrategyActivity）→ 图策略（GraphAgent）

### 3. EventHandler（事件处理器）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../event/`
- 说明：Agent 生命周期事件回调系统，支持 7 个事件类别（AGENT/TOOL/LLM/STRATEGY/NODE/SUBGRAPH/STREAMING）。
- 集成方式：在 MainActivity.buildEventHandler() 中构建，实时在聊天界面显示 Agent 执行过程（工具调用、LLM 请求、节点执行等）。与 Tracing（纯数据记录）职责分离。
- 配置入口：设置 → 事件处理器（EventHandlerConfigActivity），支持按类别过滤事件。

### 4. Snapshot（执行快照/断点续传）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../snapshot/`
- 说明：Agent 执行状态持久化，支持自动/手动检查点、内存/文件两种存储、3 种回滚策略。
- 集成方式：在 MainActivity.buildPersistence() 中构建，支持内存存储和文件存储，可配置自动检查点和回滚策略。
- 配置入口：设置 → 执行快照（SnapshotConfigActivity），支持查看/清除检查点。

### 5. Tracing（执行追踪）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../trace/`
- 说明：Agent 执行过程的结构化追踪记录，支持 20+ 种 TraceEvent 类型，输出到 Android Log 和/或文件。
- 集成方式：在 MainActivity.buildTracing() 中构建，支持 LogTraceWriter 和 FileTraceWriter。
- 配置入口：设置 → 执行追踪（TraceConfigActivity），支持开关文件输出和按事件类别过滤。

### 6. SimpleLLMPlanner（任务规划）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../planner/SimpleLLMPlanner.kt`
- 说明：基于 LLM 的任务规划器，支持可选的 Critic 评估（SimpleLLMWithCriticPlanner）。
- 集成方式：在 MainActivity 中，Agent 模式下可选启用，发送消息前先调用规划器生成结构化计划，再执行 Agent Loop。
- 配置入口：设置 → 任务规划（PlannerConfigActivity），支持配置最大迭代次数和 Critic 开关。

## 未集成模块

### 7. A2A（Agent-to-Agent 协议）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../a2a/`
- 说明：多 Agent 通信协议，包含客户端、服务端、传输层、会话管理、推送通知。
- 未集成原因：需要多 Agent 场景和网络服务端，单设备单 Agent 场景暂不需要。
- 后续方向：实现多设备协作或云端 Agent 调度时集成。

### 8. GOAP Planner（目标导向规划）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../planner/GOAPPlanner.kt`
- 说明：基于 A* 搜索的目标导向动作规划器，需要开发者定义 Action/Goal/State。
- 未集成原因：需要开发者定义领域特定的动作和目标数据结构，不适合通用 UI。
- 后续方向：可为特定场景（如文件整理、代码重构）预定义 GOAP 动作集，通过 UI 选择。
