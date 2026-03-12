# Repository抽离与功能域拆分方案

## 文档目的

本文档用于记录：在项目准备统一改造成 MVVM 的前提下，`app` 层如果先从 Repository 抽离开始，**应该拆成多少个 Repository、每个 Repository 负责什么功能、对应哪些现有页面、哪些类不应该硬改成 Repository**。

## 结论先说

基于当前项目的功能页、设置页、聊天主链、RAG、MCP、Checkpoint、MNN、本地与远程配置入口的实际代码，推荐的主方案是：

> **在 `app/src/main/java/com/lhzkml/jasmine/repository/` 统一建立 Repository 目录，并按功能域拆成 21 个 Repository 文件。**

这个数量是按**功能域**而不是按单个页面拆的：

- 不会做成一个总 Repository
- 也不会一页一个 Repository
- 而是让**同一功能域的页面共用一个 Repository**

## 一、拆分原则

### 1. Repository 按“功能域”拆，不按“UI 页面”拆

例如：

- `TokenManagementActivity`、`SamplingParamsConfigActivity`、`SystemPromptConfigActivity`
  - 虽然是三个页面
  - 但都属于 **LLM 参数域**
  - 所以归到同一个 `LlmSettingsRepository`

### 2. Settings 首页不单独建 `SettingsRepository`

`SettingsActivity` 现在只是一个“汇总页”，它自己没有独立的数据域。它展示的是：

- 模型供应商摘要
- RAG 摘要
- MCP 摘要
- Trace 摘要
- Planner 摘要
- Snapshot 摘要
- Compression 摘要
- Rules 摘要

因此：

> **不要新建 Mega 级的 `SettingsRepository`。**

正确做法是：

- `SettingsViewModel` 依赖多个 Repository
- 或由 `SettingsSummaryUseCase` 聚合多个 Repository 数据

### 3. 纯运行时能力不要硬做成 Repository

下面这些不应该为了“统一风格”被硬改成 Repository：

- `ChatExecutor`
- `AgentRuntimeBuilder`
- `ToolRegistryBuilder`
- `WakeLockManager`
- `BatteryOptimizationHelper`
- `McpConnectionManager`
- `RagStore`
- `MnnDownloadManager`
- JNI / C++

它们应该继续保留为：

- Service
- Manager
- Builder
- Runtime
- Adapter

Repository 只负责：

- 配置读取/保存
- 本地数据读写
- 资源装载
- 对底层 service/core 的数据型封装

## 二、推荐 Repository 总数：21 个

统一目录建议：

> `app/src/main/java/com/lhzkml/jasmine/repository/`

统一文件约定建议：

- **一个 Repository 一个 `.kt` 文件**
- 文件名就用 Repository 名称
- 每个文件内部可以同时放：
  - `interface XxxRepository`
  - `class DefaultXxxRepository`

## 三、21 个 Repository 清单

| 序号 | 文件名 | 功能域 | 当前主要数据来源 / 依赖 |
|---|---|---|---|
| 1 | `SessionRepository.kt` | 启动模式 / Agent 模式 / 工作区 / 上次会话 | `ConfigRepository` |
| 2 | `ChatConversationRepository.kt` | 对话列表 / 消息历史 / 当前会话持久化 | 现有 core `ConversationRepository` |
| 3 | `ProviderRepository.kt` | Provider 列表 / 当前激活 Provider / API Key / BaseUrl / 自定义 Provider / Vertex AI | `ProviderRegistry` + `ConfigRepository` |
| 4 | `ModelSelectionRepository.kt` | 当前模型 / 模型列表 / selectedModels / Thinking 开关 | `ConfigRepository` + `MnnModelManager` |
| 5 | `LlmSettingsRepository.kt` | System Prompt / MaxTokens / Temperature / TopP / TopK | `ConfigRepository` |
| 6 | `TimeoutSettingsRepository.kt` | Request/Socket/Connect Timeout / Stream Resume | `ConfigRepository` |
| 7 | `ToolSettingsRepository.kt` | Tools 启用 / Tool 预设 / BrightData Key | `ConfigRepository` |
| 8 | `ShellPolicyRepository.kt` | Shell Policy / 黑白名单 | `ConfigRepository` |
| 9 | `McpRepository.kt` | MCP 开关 / 服务器列表 / 连接状态 / 重连 / 清缓存 | `ConfigRepository` + `McpConnectionManager` |
| 10 | `AgentStrategyRepository.kt` | Agent Strategy / Tool Choice / Tool Selection / Max Iterations / Tool Result Length | `ConfigRepository` |
| 11 | `TraceSettingsRepository.kt` | Trace 开关 / 文件输出 / 事件过滤 | `ConfigRepository` |
| 12 | `PlannerSettingsRepository.kt` | Planner 开关 / 最大迭代 / Critic | `ConfigRepository` |
| 13 | `SnapshotSettingsRepository.kt` | Snapshot 开关 / 存储方式 / Auto Checkpoint / Rollback Strategy | `ConfigRepository` |
| 14 | `EventHandlerSettingsRepository.kt` | EventHandler 开关 / 事件过滤 | `ConfigRepository` |
| 15 | `CompressionSettingsRepository.kt` | 压缩开关 / 策略 / 阈值 / LastN / Chunk / KeepRecentRounds | `ConfigRepository` |
| 16 | `RulesRepository.kt` | Personal Rules / Project Rules | `ConfigRepository` |
| 17 | `RagConfigRepository.kt` | RAG 开关 / TopK / Embedding 配置 / ActiveLibraries / Extensions | `ConfigRepository` + `RagStore` + `MnnModelManager` |
| 18 | `RagLibraryRepository.kt` | 知识库内容 / 索引任务 / 文档浏览 / 文档删除 | `RagStore` |
| 19 | `CheckpointRepository.kt` | Checkpoint 列表 / 详情 / 删除 / 清空 / 恢复 | `CheckpointService` + core `ConversationRepository` |
| 20 | `MnnModelRepository.kt` | 本地模型 / 模型市场 / 导入导出 / 下载 / 配置保存 | `MnnModelManager` + `MnnDownloadManager` |
| 21 | `AboutRepository.kt` | 版本信息 / MNN 版本 / OSS Licenses 列表与正文 | `PackageManager` + `BuildConfig` + `MnnBridge` + `OssLicenseLoader` |

## 四、每个 Repository 的职责边界

### 1. `SessionRepository.kt`

负责：

- `isAgentMode`
- `setAgentMode`
- `workspacePath`
- `workspaceUri`
- `lastConversationId`
- `lastSession`

对应页面/功能：

- `LauncherActivity`
- `ChatViewModel` 中工作区恢复/退出工作区

说明：

- 文件选择器、权限请求、跳系统设置页不属于 Repository，属于 View 层 effect

### 2. `ChatConversationRepository.kt`

负责：

- 创建对话
- 加载对话
- 删除对话
- 监听对话列表
- 读写消息历史

对应页面/功能：

- `ChatViewModel`
- `ConversationViewModel`
- `DrawerContent`

说明：

- 推荐用 app 层包装 core 的 `ConversationRepository`
- 不建议直接在 ViewModel/Activity 里 new core `ConversationRepository`

### 3. `ProviderRepository.kt`

负责：

- 获取所有 provider
- 当前激活 provider
- 保存 provider 配置
- API key / baseUrl / model / chatPath
- 自定义 provider 的增删改
- Vertex AI 相关配置

对应页面/功能：

- `ProviderListActivity`
- `ProviderConfigActivity`
- `AddCustomProviderActivity`
- `SettingsActivity` 中 Provider 摘要

### 4. `ModelSelectionRepository.kt`

负责：

- 当前模型选择
- selectedModels 列表
- 本地模型候选读取
- Thinking 模式开关

对应页面/功能：

- `ModelViewModel`
- `ChatViewModel` 中模型切换相关逻辑
- 聊天页模型选择器

说明：

- 它不负责模型文件导入导出
- 模型文件管理属于 `MnnModelRepository`

### 5. `LlmSettingsRepository.kt`

负责：

- 默认系统提示词
- Max Tokens
- Temperature
- TopP
- TopK

对应页面/功能：

- `SystemPromptConfigActivity`
- `TokenManagementActivity`
- `SamplingParamsConfigActivity`
- `SettingsActivity` 中 LLM 参数摘要

### 6. `TimeoutSettingsRepository.kt`

负责：

- Request Timeout
- Socket Timeout
- Connect Timeout
- Stream Resume 开关与重试次数

对应页面/功能：

- `TimeoutConfigActivity`
- `ChatExecutor` 中超时读取

### 7. `ToolSettingsRepository.kt`

负责：

- Tools 总开关
- Enabled Tools
- Agent Tool Preset
- BrightData Key

对应页面/功能：

- `ToolConfigActivity`
- `LauncherActivity` 中进入工作区时的默认工具开关
- `SettingsActivity` 中工具摘要

### 8. `ShellPolicyRepository.kt`

负责：

- Shell Policy
- Shell Blacklist
- Shell Whitelist

对应页面/功能：

- `ShellPolicyActivity`
- `SettingsActivity` 中 Shell 策略摘要

### 9. `McpRepository.kt`

负责：

- MCP 启用开关
- MCP Server 列表增删改
- 连接状态查询
- reconnect
- connectSingleServerByName
- clearServerCache

对应页面/功能：

- `McpServerActivity`
- `McpServerEditActivity`
- `SettingsActivity` 中 MCP 摘要
- `ChatViewModel` 中预连接与工具装载

说明：

- `McpConnectionManager` 继续保留为底层 service
- Repository 负责把“配置 + 状态 + service 调用”整成 ViewModel 可用的数据口

### 10. `AgentStrategyRepository.kt`

负责：

- Agent Strategy
- Graph Tool Call Mode
- Tool Selection Strategy / Names / TaskDesc
- Tool Choice Mode / NamedTool
- Agent MaxIterations
- MaxToolResultLength

对应页面/功能：

- `AgentStrategyActivity`
- `ChatExecutor` 中 agent 执行参数读取

### 11. `TraceSettingsRepository.kt`

负责：

- Trace 开关
- File 输出开关
- TraceEventFilter

对应页面/功能：

- `TraceConfigActivity`
- `LauncherActivity` 中默认 trace 开关
- `SettingsActivity` 中 trace 摘要

### 12. `PlannerSettingsRepository.kt`

负责：

- Planner 开关
- Planner 最大迭代
- Critic 开关

对应页面/功能：

- `PlannerConfigActivity`
- `ChatExecutor` 中 planner 参数读取

### 13. `SnapshotSettingsRepository.kt`

负责：

- Snapshot 开关
- Storage 类型
- Auto Checkpoint 开关
- Rollback Strategy

对应页面/功能：

- `SnapshotConfigActivity`
- `CheckpointRecovery.kt`
- `ChatViewModel` 中 snapshot 相关条件判断

### 14. `EventHandlerSettingsRepository.kt`

负责：

- EventHandler 开关
- EventHandlerFilter

对应页面/功能：

- `EventHandlerConfigActivity`
- `LauncherActivity` 中默认 event handler 开关
- `SettingsActivity` 中摘要显示

### 15. `CompressionSettingsRepository.kt`

负责：

- Compression 开关
- CompressionStrategy
- MaxTokens
- Threshold
- LastN
- ChunkSize
- KeepRecentRounds

对应页面/功能：

- `CompressionConfigActivity`
- `ChatExecutor` 中上下文压缩策略读取

### 16. `RulesRepository.kt`

负责：

- Personal Rules
- Project Rules

对应页面/功能：

- `RulesActivity`
- `SettingsActivity` 中 Rules 摘要

### 17. `RagConfigRepository.kt`

负责：

- RAG 开关
- TopK
- Embedding BaseUrl / ApiKey / Model / UseLocal / ModelPath
- Libraries 元数据
- Active LibraryIds
- IndexableExtensions

对应页面/功能：

- `RagConfigActivity`
- `EmbeddingConfigActivity`
- `SettingsActivity` 中 RAG 摘要
- `ChatViewModel` 中 RAG 上下文启用配置

说明：

- 配置属于这个 Repository
- 具体知识库内容增删查、索引任务放到 `RagLibraryRepository`

### 18. `RagLibraryRepository.kt`

负责：

- 获取知识索引
- 浏览库内容
- 删除条目
- 触发索引任务
- 返回索引错误原因

对应页面/功能：

- `RagLibraryContentActivity`
- `RagConfigActivity` 中手动索引/工作区索引

### 19. `CheckpointRepository.kt`

负责：

- 列出所有 session checkpoints
- 获取单会话 checkpoints
- 获取单 checkpoint 详情
- 删除 session
- 删除 checkpoint
- 清空全部
- 获取统计信息
- 执行恢复所需数据装配

对应页面/功能：

- `CheckpointManagerActivity`
- `CheckpointDetailActivity`
- `CheckpointRecovery.kt`

说明：

- 这里不要让页面直接碰 `CheckpointService`
- 恢复逻辑也不要散落在 Activity 中

### 20. `MnnModelRepository.kt`

负责：

- 本地模型列表
- 模型配置读取/保存
- 全局默认配置
- 删除模型
- 模型市场数据读取
- 下载状态
- 导入 / 导出 / 压缩 / 解压

对应页面/功能：

- `MnnManagementActivity`
- `MnnModelMarketActivity`
- `MnnModelSettingsActivity`
- `EmbeddingConfigActivity` 中本地模型列表
- `ModelSelectionRepository` 中本地模型只读查询

说明：

- `MnnDownloadManager` 继续保留为下载 service
- Repository 封装它暴露给 ViewModel 的数据入口

### 21. `AboutRepository.kt`

负责：

- App 版本号
- `jasmine-core` 版本
- MNN 版本
- OSS licenses 列表
- OSS license 正文

对应页面/功能：

- `AboutActivity`
- `OssLicensesListActivity`
- `OssLicensesDetailActivity`

说明：

- 这部分虽然轻，但如果要全项目统一 Repository 风格，建议也收进一个轻量 Repository

## 五、当前页面到目标 Repository 的映射

| 当前页面/功能 | 目标 Repository |
|---|---|
| `LauncherActivity` | `SessionRepository` + `ToolSettingsRepository` + `TraceSettingsRepository` + `EventHandlerSettingsRepository` |
| 聊天页主链 | `ChatConversationRepository` + `ModelSelectionRepository` + `SessionRepository` + `RagConfigRepository` + 各执行设置 Repository |
| `SettingsActivity` | **不新建专属 Repository**；由多个 Repository 聚合 |
| `ProviderListActivity` | `ProviderRepository` |
| `ProviderConfigActivity` | `ProviderRepository` |
| `AddCustomProviderActivity` | `ProviderRepository` |
| `TokenManagementActivity` | `LlmSettingsRepository` |
| `SamplingParamsConfigActivity` | `LlmSettingsRepository` |
| `SystemPromptConfigActivity` | `LlmSettingsRepository` |
| `RulesActivity` | `RulesRepository` |
| `ToolConfigActivity` | `ToolSettingsRepository` |
| `AgentStrategyActivity` | `AgentStrategyRepository` |
| `ShellPolicyActivity` | `ShellPolicyRepository` |
| `CompressionConfigActivity` | `CompressionSettingsRepository` |
| `TimeoutConfigActivity` | `TimeoutSettingsRepository` |
| `TraceConfigActivity` | `TraceSettingsRepository` |
| `PlannerConfigActivity` | `PlannerSettingsRepository` |
| `SnapshotConfigActivity` | `SnapshotSettingsRepository` |
| `EventHandlerConfigActivity` | `EventHandlerSettingsRepository` |
| `RagConfigActivity` | `RagConfigRepository` + `RagLibraryRepository` |
| `EmbeddingConfigActivity` | `RagConfigRepository` + `MnnModelRepository` |
| `RagLibraryContentActivity` | `RagLibraryRepository` |
| `McpServerActivity` | `McpRepository` |
| `McpServerEditActivity` | `McpRepository` |
| `CheckpointManagerActivity` | `CheckpointRepository` |
| `CheckpointDetailActivity` | `CheckpointRepository` |
| `CheckpointRecovery.kt` | `CheckpointRepository` |
| `MnnManagementActivity` | `MnnModelRepository` |
| `MnnModelMarketActivity` | `MnnModelRepository` |
| `MnnModelSettingsActivity` | `MnnModelRepository` |
| `AboutActivity` | `AboutRepository` |
| `OssLicensesListActivity` | `AboutRepository` |
| `OssLicensesDetailActivity` | `AboutRepository` |

## 六、明确不要做成 Repository 的内容

下面这些不要为了“目录统一”强行塞进 repository 文件夹：

| 类/模块 | 应保留角色 |
|---|---|
| `ChatExecutor` | Application Service / Coordinator |
| `AgentRuntimeBuilder` | Builder |
| `ToolRegistryBuilder` | Builder |
| `RagStore` | 底层索引/Embedding Service Gateway |
| `McpConnectionManager` | 连接管理 Service |
| `MnnDownloadManager` | 下载 Service |
| `WakeLockManager` | 系统运行时 Service |
| `BatteryOptimizationHelper` | 系统能力 Helper |
| `AppNavigation` / `Routes` | Navigation |
| 文件选择器 / 权限请求 | View 层 effect |
| JNI / `mnn_jni.cpp` | Native Adapter |

## 七、为什么不是更少，也不是更多

### 为什么不能只做 1~3 个大 Repository

如果只做：

- `SettingsRepository`
- `ChatRepository`
- `AppRepository`

最后会变成：

- Repository 比现在的 `ProviderManager` 还大
- ViewModel 继续依赖超大门面
- 功能边界还是混的

这不符合“按功能抽离”的目标。

### 为什么也不建议每个页面一个 Repository

例如：

- `TokenManagementActivity`
- `SamplingParamsConfigActivity`
- `SystemPromptConfigActivity`

如果每页一个 Repository，会把同一个配置域拆得过碎。

所以最佳粒度是：

> **按功能域拆 Repository，而不是按单页面拆。**

## 八、最终推荐主方案

最终建议采用这套主方案：

> **统一建立 `repository/` 主目录，按功能域拆成 21 个 Repository 文件。**

最关键的执行原则：

1. 先拆 `Session / Provider / Conversation / LLM Settings / RAG / MCP / MNN` 这些高频主域
2. `SettingsActivity` 不单独建 Mega Repository，而是做聚合
3. 运行时类、Builder、Manager、JNI 不要硬改成 Repository
4. 页面以后只依赖对应功能域 Repository，不再直接调用 `ProviderManager`、`AppConfig` 或直接 new core `ConversationRepository`
