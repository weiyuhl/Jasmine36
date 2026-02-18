# 未集成到应用层的模块

以下模块已在 `jasmine-core` 中完整实现，但未集成到应用层（`app`）。它们属于 SDK 基础设施，当前不适合通过 UI 配置使用。

## 1. Graph Agent（图执行引擎）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../graph/`
- 说明：开发者 DSL，用于构建复杂多步骤 agent 工作流（节点、边、子图、策略）。
- 未集成原因：需要开发者编写节点/边/子图代码，不适合 UI 配置。
- 后续方向：可提供预置的图模板（如 RAG 流程、多轮搜索），让用户在 UI 中选择。

## 2. EventHandler（事件处理器）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../event/`
- 说明：Agent 生命周期钩子，用于在节点执行前后注入自定义逻辑。
- 未集成原因：纯开发者 API，终端用户无需直接操作。
- 后续方向：可作为插件系统的基础，允许用户安装预定义的事件处理插件。

## 3. MCP（Model Context Protocol）✅ 已集成

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../mcp/`
- 说明：MCP 工具注册提供者，支持从 MCP 服务器动态加载工具。
- 状态：已集成到应用层。支持配置多个 MCP 服务器（HTTP JSON-RPC），Agent 模式下自动连接并加载远程工具。

## 4. A2A（Agent-to-Agent 协议）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../a2a/`
- 说明：多 agent 通信协议，包含客户端、服务端、传输层、会话管理、推送通知。
- 未集成原因：需要多 agent 场景和网络服务端，单设备单 agent 场景暂不需要。
- 后续方向：实现多设备协作或云端 agent 调度时集成。

## 5. Snapshot（断点续传）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../snapshot/`
- 说明：Agent 执行状态持久化，支持长任务中断后恢复。
- 未集成原因：当前 Android 端对话较短，断点续传优先级低。
- 后续方向：在 Agent 模式下添加自动保存检查点，应用被杀后可恢复执行。

## 6. GOAP Planner（目标导向规划）

- 路径：`jasmine-core/agent/agent-tools/src/main/java/.../planner/GOAPPlanner.kt`
- 说明：基于 A* 搜索的目标导向动作规划器，需要开发者定义 Action/Goal/State。
- 未集成原因：需要开发者定义领域特定的动作和目标数据结构，不适合通用 UI。
- 后续方向：可为特定场景（如文件整理、代码重构）预定义 GOAP 动作集，通过 UI 选择。
