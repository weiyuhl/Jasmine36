# 核心框架层与应用层分离分析（最终版）

## 分析目标
识别 `app` 层中哪些代码包含核心业务逻辑，应该迁移到 `jasmine-core` 核心框架层。

---

## 已完成的分离 ✅

| 模块 | 说明 |
|------|------|
| `jasmine-core/config/config-manager` | ConfigRepository 接口 + 数据模型 |
| `jasmine-core/prompt/prompt-model` | ChatMessage/ChatRequest/ChatResponse |
| `jasmine-core/prompt/prompt-llm` | ChatClient/LLMSession/ContextManager/SystemContextProvider |
| `jasmine-core/prompt/prompt-executor` | 供应商适配器（OpenAI/Claude/Gemini/DeepSeek） |
| `jasmine-core/agent/agent-tools` | 工具系统/ToolExecutor/GraphAgent/MCP/Tracing/Event/Snapshot/Planner |
| `jasmine-core/agent/agent-dex` | DEX/APK 编辑工具 |
| `jasmine-core/agent/agent-runtime` | **新增** — Agent 运行时编排（工具注册、MCP 连接、检查点服务、追踪/事件/持久化/压缩构建） |
| `jasmine-core/conversation/conversation-storage` | 对话历史持久化 |
| `app/SharedPreferencesConfigRepository` | Android 平台配置实现 |
| `app/ProviderManager` | 向后兼容的门面层 |
| `app/AppConfig` | 全局实例管理（ConfigRepository、McpConnectionManager、CheckpointService） |

---

## agent-runtime 模块详情

### 新建文件

| 文件 | 职责 | 迁移来源 |
|------|------|----------|
| `ToolRegistryBuilder.kt` | 根据配置构建工具注册表 | `MainActivity.buildToolRegistry()` |
| `McpConnectionManager.kt` | MCP 客户端生命周期、连接状态、工具加载 | `MainActivity.preconnectMcpServers()` + `loadMcpToolsInto()` + companion object |
| `AgentRuntimeBuilder.kt` | 构建 Tracing/EventHandler/Persistence/SystemContextCollector | `MainActivity.buildTracing/buildEventHandler/buildPersistence/refreshContextCollector` |
| `CompressionStrategyBuilder.kt` | 根据配置构建压缩策略 | `MainActivity.buildCompressionStrategy()` |
| `CheckpointService.kt` | 检查点 CRUD 接口 + `FileCheckpointService` 实现 | `CheckpointManagerActivity` + `CheckpointDetailActivity` 中的文件操作逻辑 |

### app 层修改

| 文件 | 修改内容 |
|------|----------|
| `MainActivity.kt` | 所有 build* 方法委托给 core 层；使用 `AppConfig.mcpConnectionManager()` 共享实例；`tryOfferStartupRecovery` 使用 `CheckpointService` |
| `McpServerActivity.kt` | `autoConnectAll()` 使用 `AppConfig.mcpConnectionManager()` 共享实例 |
| `CheckpointManagerActivity.kt` | 使用 `AppConfig.checkpointService()` 替代直接操作 `FilePersistenceStorageProvider` |
| `CheckpointDetailActivity.kt` | 使用 `AppConfig.checkpointService()` 替代直接操作 `FilePersistenceStorageProvider` |
| `AppConfig.kt` | 新增 `mcpConnectionManager()` 和 `checkpointService()` 全局实例 |

---

## 不需要迁移的文件（纯 UI 层）

以下文件是纯 Android UI 代码，正确留在 app 层：

- `LauncherActivity` — 启动页，文件夹选择器
- `SettingsActivity` — 设置页
- `ProviderListActivity` / `ProviderConfigActivity` / `ProviderConfigFragment` — 供应商配置 UI
- `ModelListFragment` — 模型列表 UI
- `ToolConfigActivity` — 工具配置 UI
- `AgentStrategyActivity` — Agent 策略配置 UI
- `ShellPolicyActivity` — Shell 策略配置 UI
- `McpServerActivity` / `McpServerEditActivity` — MCP 服务器管理 UI
- `TraceConfigActivity` — 追踪配置 UI
- `EventHandlerConfigActivity` — 事件处理器配置 UI
- `SnapshotConfigActivity` — 快照配置 UI
- `PlannerConfigActivity` — 规划器配置 UI
- `CompressionConfigActivity` — 压缩配置 UI
- `TimeoutConfigActivity` — 超时配置 UI
- `FileTreeAdapter` — 文件树 UI 适配器
- `JasmineApplication` — Application 初始化
- `ProviderManager` — 向后兼容门面
- `SharedPreferencesConfigRepository` — Android 平台实现

---

## 迁移原则

1. core 层不依赖 Android API（Context/Activity/SharedPreferences/Toast/AlertDialog）
2. UI 回调通过接口/lambda 参数传入（如 `ConnectionListener`、`EventEmitter`、`shellConfirmationHandler`）
3. 平台相关路径（如 `getExternalFilesDir`）通过参数传入
4. 全局共享实例通过 `AppConfig` 管理（`McpConnectionManager`、`CheckpointService`）
5. 保持向后兼容，app 层调用方式逐步迁移
