# Jasmine — Android AI Agent 框架

> 一个模块化的 Android 端 AI Agent 框架，支持 LLM 调用、工具执行、图策略、MCP 协议和 Agent-to-Agent 通信。

## 项目信息

| 项 | 值 |
|---|---|
| 包名 | `com.lhzkml.jasmine` |
| 语言 | Kotlin |
| 平台 | Android (minSdk 26 / targetSdk 36) |
| 构建系统 | Gradle Kotlin DSL |
| JVM Target | 17 |

---

## 架构总览

项目采用 **框架层 + 应用层** 的多模块架构，核心逻辑全部在 `jasmine-core` 中，`app` 仅负责 UI 和 Android 平台适配。

```
Jasmine/
├── app/                              # 应用层 — Android UI、Activity
└── jasmine-core/                     # 框架层 — 纯业务逻辑
    ├── prompt/
    │   ├── prompt-model/             # 数据模型
    │   ├── prompt-llm/               # LLM 通信
    │   └── prompt-executor/          # ChatClient 工厂 & 路由
    ├── agent/
    │   ├── agent-tools/              # 工具注册 & 执行
    │   ├── agent-graph/              # 图执行引擎 & Feature 系统
    │   ├── agent-observe/            # 可观测性 (Trace / Log)
    │   ├── agent-planner/            # Agent 规划器
    │   ├── agent-mcp/                # MCP 协议
    │   ├── agent-a2a/                # Agent-to-Agent 协议
    │   └── agent-runtime/            # Agent 运行时
    ├── conversation/
    │   └── conversation-storage/     # Room 本地持久化
    └── config/
        └── config-manager/           # 配置管理 & Provider 注册
```

---

## 模块详解

### Prompt 层

| 模块 | 职责 |
|---|---|
| **prompt-model** | 定义核心数据模型：`ChatMessage`、`Prompt`、`ToolDescriptor` 等 |
| **prompt-llm** | LLM 客户端封装、Token 估算、上下文压缩策略 |
| **prompt-executor** | ChatClient 工厂和路由，根据配置选择对应的 LLM Provider |

### Agent 层

| 模块 | 职责 |
|---|---|
| **agent-tools** | 工具注册表、`ToolExecutor`（Agent Loop 核心） |
| **agent-graph** | 图执行引擎，包含 Feature 系统、事件处理器（LLM Call / Streaming / Tool Call / Node Execution / Strategy / Subgraph）、策略模式 |
| **agent-observe** | 提供 Tracing 和 Logging 的可观测能力 |
| **agent-planner** | Agent 任务规划能力 |
| **agent-mcp** | Model Context Protocol 客户端支持 |
| **agent-a2a** | Agent-to-Agent 通信协议实现（包含客户端 `A2AClient` 和服务端 `A2AServer`、Session 管理、Push 通知等） |
| **agent-runtime** | Agent 运行时协调与启动 |

### 基础设施层

| 模块 | 职责 |
|---|---|
| **conversation-storage** | 使用 Room 数据库持久化对话记录 |
| **config-manager** | 配置仓库接口（`ConfigRepository`）、Provider 注册表（`ProviderRegistry`） |

---

## 模块依赖关系

```
app → jasmine-core (所有子模块)

agent-tools     → prompt-model, prompt-llm
agent-runtime   → agent-tools, prompt-model, prompt-llm
prompt-llm      → prompt-model
prompt-executor → prompt-llm, prompt-model
config-manager  → prompt-model, prompt-executor
conversation-storage → prompt-model
```

> 依赖方向：只能从上往下，`jasmine-core` 各模块不允许反向依赖 `app`。

---

## App 层界面

| Activity | 功能 |
|---|---|
| `LauncherActivity` | 启动入口 |
| `MainActivity` | 主聊天界面 |
| `SettingsActivity` | 通用设置 |
| `ProviderListActivity` / `ProviderConfigActivity` | LLM Provider 管理 |
| `AgentStrategyActivity` | Agent 策略配置 |
| `ToolConfigActivity` | 工具启用/禁用 |
| `McpServerActivity` / `McpServerEditActivity` | MCP 服务器管理 |
| `PlannerConfigActivity` | 规划器配置 |
| `CompressionConfigActivity` | 上下文压缩配置 |
| `CheckpointManagerActivity` / `CheckpointDetailActivity` | 检查点管理 |
| `TraceConfigActivity` | Trace 配置 |
| `EventHandlerConfigActivity` | 事件处理器配置 |
| `ShellPolicyActivity` | Shell 策略配置 |
| `SnapshotConfigActivity` | 快照配置 |
| `TimeoutConfigActivity` | 超时配置 |

---

## 技术栈

- **Kotlin** + Kotlin Serialization
- **Kotlin Coroutines** (core + android)
- **Room** 数据库
- **AndroidX** (AppCompat, Core KTX, Material, ViewPager2)
- **Gradle Kotlin DSL** 多模块构建

---

## 开发规范摘要

1. **框架与应用分离** — 所有业务逻辑必须在 `jasmine-core`，`app` 只做 UI
2. **按功能拆模块** — 每个模块单一职责，新功能优先新建模块
3. **不写向后兼容代码** — 重构后直接删除旧 API
4. **不写数据库迁移** — 使用 `fallbackToDestructiveMigration`
5. **必须写单元测试** — 每个 core 模块的公开类都要有测试

## 开发流程

```bash
# 1. 运行测试
.\gradlew.bat test

# 2. 构建 APK
.\gradlew.bat assembleDebug
```
