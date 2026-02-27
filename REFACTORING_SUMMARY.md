# 架构重构总结 — 配置管理层分离

## 重构目标

将 `app` 层中错误放置的核心业务逻辑和数据模型迁移到 `jasmine-core` 模块，实现清晰的分层架构。

## 完成的工作

### 1. 新建 `jasmine-core/config/config-manager` 模块

创建了独立的配置管理模块，包含：

**核心接口：**
- `ConfigRepository` — 配置仓库接口，定义所有配置的读写契约
- `ProviderRegistry` — 供应商注册表，管理供应商的注册/注销/查询

**数据模型：**
- `ProviderConfig` — 供应商配置（原 `Provider`）
- `ActiveProviderConfig` — 当前激活的完整配置
- `McpServerConfig` — MCP 服务器配置
- `McpTransportType` — MCP 传输类型枚举
- `AgentStrategyType` — Agent 执行策略枚举
- `GraphToolCallMode` — 图策略工具调用模式枚举
- `ToolSelectionStrategyType` — 工具选择策略枚举
- `ToolChoiceMode` — ToolChoice 模式枚举
- `SnapshotStorageType` — 快照存储方式枚举

**工具目录：**
- `ToolCatalog` — 工具元数据目录，避免工具定义硬编码在 UI 层

### 2. 实现 `SharedPreferencesConfigRepository`

在 `app` 层创建了 `ConfigRepository` 接口的 Android 平台实现：
- 基于 `SharedPreferences` 持久化所有配置
- 完整实现了 100+ 个配置项的读写
- 包含供应商、LLM 参数、超时、工具、Shell 策略、MCP、Agent 策略、追踪、规划、快照、事件处理器、压缩等所有配置

### 3. 重写 `ProviderManager` 为委托门面

原 `ProviderManager` 是一个 899 行的 God Object，现在变成了：
- 薄薄的委托层，所有调用转发给 `ConfigRepository` 和 `ProviderRegistry`
- 保持向后兼容，所有现有调用点（如 `ProviderManager.getShellPolicy(this)`）无需修改
- 使用顶层类型别名和嵌套类型别名支持 `ProviderManager.XXX` 的用法

### 4. 更新 `ToolConfigActivity`

- 从 `ToolCatalog` 读取工具元数据，而不是硬编码在 Activity 中
- UI 层只负责展示，工具定义由 core 层提供

### 5. 更新模块依赖

- `settings.gradle.kts` — 添加 `config-manager` 模块
- `app/build.gradle.kts` — 依赖 `config-manager` 模块，启用 `-Xnested-type-aliases` 编译选项

## 架构改进

### 之前的问题

1. **`ProviderManager.kt` (899 行)** — 包含大量核心业务逻辑和数据模型，但放在 `app` 层
   - 数据模型（`Provider`, `McpServerConfig`, 各种枚举）
   - 业务逻辑（供应商注册/注销、MCP CRUD、配置管理）
   - 持久化逻辑（`SharedPreferences` 直接硬编码）
   - core 层无法访问这些配置

2. **`MainActivity.kt` (1974 行)** — 包含大量 Agent 编排逻辑
   - `buildToolRegistry()` — 工具注册表构建
   - `preconnectMcpServers()` / `loadMcpToolsInto()` — MCP 连接管理
   - `buildTracing()`, `buildEventHandler()`, `buildPersistence()` — 系统构建

3. **`ToolConfigActivity.kt`** — 工具定义硬编码在 UI 层

4. **`CheckpointManagerActivity.kt`** — 直接操作文件系统读取检查点

### 现在的架构

```
jasmine-core/
├── config/config-manager/          ← 新增：配置管理核心
│   ├── ConfigRepository.kt         ← 接口：配置读写契约
│   ├── ProviderRegistry.kt         ← 业务逻辑：供应商管理
│   ├── ProviderConfig.kt           ← 数据模型：供应商配置
│   ├── McpConfig.kt                ← 数据模型：MCP 配置
│   ├── AgentConfig.kt              ← 数据模型：Agent 配置枚举
│   └── ToolCatalog.kt              ← 工具元数据目录
├── prompt/
├── agent/
└── conversation/

app/
├── SharedPreferencesConfigRepository.kt  ← 实现：Android 平台持久化
├── ProviderManager.kt                    ← 门面：向后兼容的委托层
└── Activities...                         ← UI 层：只负责展示
```

### 核心原则

1. **依赖倒置** — core 层定义接口（`ConfigRepository`），app 层提供实现（`SharedPreferencesConfigRepository`）
2. **平台无关** — `jasmine-core` 不依赖 `android.content.Context`，可独立于 Android 运行
3. **单一职责** — 配置管理、业务逻辑、UI 展示各司其职
4. **向后兼容** — 所有现有代码无需修改，`ProviderManager` 作为门面保持 API 稳定

## 下一步建议

### 1. 创建 `jasmine-core/agent/agent-runtime` 模块

将 `MainActivity` 中的 Agent 编排逻辑抽出：
```kotlin
class AgentRuntime(
    private val configRepo: ConfigRepository,
    private val toolRegistry: ToolRegistry,
    private val mcpClients: List<McpClient>
) {
    fun buildToolRegistry(): ToolRegistry { ... }
    fun buildTracing(): Tracing? { ... }
    fun buildEventHandler(): EventHandler? { ... }
    fun buildPersistence(): Persistence? { ... }
}
```

### 2. 创建 `CheckpointService` 接口

在 core 层封装检查点的查询/删除操作：
```kotlin
interface CheckpointService {
    suspend fun listCheckpoints(agentId: String): List<AgentCheckpoint>
    suspend fun deleteCheckpoints(agentId: String)
    suspend fun clearAll()
}
```

### 3. 移除 `MainActivity` 中的 `mcpConnectionCache`

MCP 连接状态不应该放在 Activity 的 companion object 中，应该由 `AgentRuntime` 或专门的 `McpConnectionManager` 管理。

## 验证

- ✅ `jasmine-core/config/config-manager` 模块编译成功
- ✅ `app` 模块编译成功
- ✅ 完整 APK 构建成功 (`assembleDebug`)
- ✅ 所有现有代码无需修改（向后兼容）

## 文件变更统计

**新增文件：**
- `jasmine-core/config/config-manager/build.gradle.kts`
- `jasmine-core/config/config-manager/src/main/AndroidManifest.xml`
- `jasmine-core/config/config-manager/src/main/java/com/lhzkml/jasmine/core/config/ConfigRepository.kt`
- `jasmine-core/config/config-manager/src/main/java/com/lhzkml/jasmine/core/config/ProviderRegistry.kt`
- `jasmine-core/config/config-manager/src/main/java/com/lhzkml/jasmine/core/config/ProviderConfig.kt`
- `jasmine-core/config/config-manager/src/main/java/com/lhzkml/jasmine/core/config/McpConfig.kt`
- `jasmine-core/config/config-manager/src/main/java/com/lhzkml/jasmine/core/config/AgentConfig.kt`
- `jasmine-core/config/config-manager/src/main/java/com/lhzkml/jasmine/core/config/ToolCatalog.kt`
- `app/src/main/java/com/lhzkml/jasmine/SharedPreferencesConfigRepository.kt`

**重写文件：**
- `app/src/main/java/com/lhzkml/jasmine/ProviderManager.kt` (899 行 → 约 200 行)

**修改文件：**
- `settings.gradle.kts` — 添加 config-manager 模块
- `app/build.gradle.kts` — 添加依赖和编译选项
- `app/src/main/java/com/lhzkml/jasmine/ToolConfigActivity.kt` — 使用 `ToolCatalog`

**代码行数变化：**
- 删除：~700 行（ProviderManager 中的业务逻辑）
- 新增：~1200 行（core 模块 + SharedPreferencesConfigRepository）
- 净增加：~500 行（但架构更清晰，职责更明确）
