# Repository 迁移未完成问题清单

## 概述

虽然已创建 21 个 Repository 并完成基础迁移，但仍有大量代码直接使用 `ProviderManager` 和 `AppConfig`，未完全迁移到 Repository 模式。

## 一、ChatViewModel.kt - 32 处 ProviderManager 未迁移

### 1. 初始化相关
- **第165行**: `ProviderManager.initialize(context)` 
  - 应该：移除或移到 Application 层

### 2. Session 相关（应使用 SessionRepository）
- **第280行**: `ProviderManager.isAgentMode(ctx)`
- **第281行**: `ProviderManager.getWorkspacePath(ctx)`
- **第384行**: `ProviderManager.isAgentMode(ctx)`
- **第385行**: `ProviderManager.getWorkspacePath(ctx)`
- **第394行**: `ProviderManager.isAgentMode(ctx)`
- **第396行**: `ProviderManager.getWorkspacePath(ctx)`
- **第415行**: `ProviderManager.getWorkspacePath(ctx)`
- **第495行**: `ProviderManager.isAgentMode(ctx)`
- **第496行**: `ProviderManager.setLastConversationId(ctx, currentConversationId ?: "")`
- **第497行**: `ProviderManager.getWorkspaceUri(ctx)`
- **第507行**: `ProviderManager.setWorkspacePath(ctx, "")`
- **第508行**: `ProviderManager.setWorkspaceUri(ctx, "")`
- **第510行**: `ProviderManager.setAgentMode(ctx, false)`
- **第511行**: `ProviderManager.setLastSession(ctx, false)`

### 3. Provider 相关（应使用 ProviderRepository）
- **第282行**: `ProviderManager.getActiveId()`
- **第284行**: `ProviderManager.getModel(ctx, activeId)`
- **第428行**: `ProviderManager.getActiveId()`
- **第436行**: `ProviderManager.getProvider(activeId)`
- **第440行**: `ProviderManager.getModel(ctx, activeId)`
- **第458行**: `ProviderManager.getModel(ctx, activeId)`
- **第459行**: `ProviderManager.getSelectedModels(ctx, activeId)`
- **第477行**: `ProviderManager.getActiveId()`
- **第479行**: `ProviderManager.getApiKey(ctx, activeId)`
- **第480行**: `ProviderManager.getBaseUrl(ctx, activeId)`
- **第481行**: `ProviderManager.saveConfig(ctx, activeId, key, baseUrl, model)`
- **第717行**: `ProviderManager.getProvider(config.providerId)`
- **第745行**: `ProviderManager.getActiveConfig()`

### 4. RAG 相关（应使用 RagConfigRepository）
- **第288行**: `ProviderManager.getRagActiveLibraryIds(ctx)`
- **第289行**: `ProviderManager.getRagLibraries(ctx)`
- **第292行**: `ProviderManager.isRagEnabled(ctx)`
- **第293行**: `ProviderManager.getRagTopK(ctx)`
- **第294行**: `ProviderManager.getRagEmbeddingBaseUrl(ctx)`
- **第295行**: `ProviderManager.getRagEmbeddingApiKey(ctx)`
- **第296行**: `ProviderManager.getRagEmbeddingModel(ctx)`
- **第297行**: `ProviderManager.getRagEmbeddingUseLocal(ctx)`
- **第298行**: `ProviderManager.getRagEmbeddingModelPath(ctx)`

### 5. Model Selection 相关（应使用 ModelSelectionRepository）
- **第452行**: `ProviderManager.getMnnThinkingEnabled(ctx, selectedModel)`
- **第489行**: `ProviderManager.setMnnThinkingEnabled(ctx, state.currentModel, enabled)`

### 6. Snapshot 相关（应使用 SnapshotSettingsRepository）
- **第573行**: `ProviderManager.isSnapshotEnabled(ctx)`
- **第574行**: `ProviderManager.getSnapshotStorage(ctx)`

### 7. Timeout 相关（应使用 TimeoutSettingsRepository）
- **第729行**: `ProviderManager.getRequestTimeout(ctx)`
- **第730行**: `ProviderManager.getConnectTimeout(ctx)`
- **第731行**: `ProviderManager.getSocketTimeout(ctx)`

### 8. Tool Registry 相关（应使用 SessionRepository）
- **第311行**: `toolRegistryBuilder.workspacePath = ProviderManager.getWorkspacePath(ctx)`
- **第343行**: `return toolRegistryBuilder.build(ProviderManager.isAgentMode(ctx))`

---

## 二、ChatExecutor.kt - 23 处 ProviderManager 未迁移

### 1. Tool 相关（应使用 ToolSettingsRepository）
- **第103行**: `ProviderManager.isToolsEnabled(context)`

### 2. LLM Settings 相关（应使用 LlmSettingsRepository）
- **第115行**: `ProviderManager.getDefaultSystemPrompt(context)`
- **第130行**: `ProviderManager.getDefaultSystemPrompt(context)`
- **第145行**: `ProviderManager.getMaxTokens(context)`
- **第148行**: `ProviderManager.getTemperature(context)`
- **第149行**: `ProviderManager.getTopP(context)`
- **第150行**: `ProviderManager.getTopK(context)`

### 3. Session 相关（应使用 SessionRepository）
- **第122行**: `ProviderManager.isAgentMode(context)`
- **第123行**: `ProviderManager.getWorkspacePath(context)`

### 4. Compression 相关（应使用 CompressionSettingsRepository）
- **第212行**: `ProviderManager.isCompressionEnabled(context)`

### 5. Agent Strategy 相关（应使用 AgentStrategyRepository）
- **第306行**: `ProviderManager.getAgentMaxIterations(context)`
- **第310行**: `ProviderManager.getMaxToolResultLength(context)`
- **第396行**: `ProviderManager.getAgentStrategy(context)`
- **第410行**: `ProviderManager.getToolChoiceMode(context)`
- **第416行**: `ProviderManager.getToolChoiceNamedTool(context)`
- **第496行**: `ProviderManager.getGraphToolCallMode(context)`
- **第502行**: `ProviderManager.getToolSelectionStrategy(context)`
- **第506行**: `ProviderManager.getToolSelectionNames(context)`
- **第510行**: `ProviderManager.getToolSelectionTaskDesc(context)`
- **第537行**: `ProviderManager.getToolChoiceMode(context)`
- **第544行**: `ProviderManager.getToolChoiceNamedTool(context)`

### 6. Planner 相关（应使用 PlannerSettingsRepository）
- **第321行**: `ProviderManager.isPlannerEnabled(context)`
- **第348行**: `ProviderManager.getPlannerMaxIterations(context)`
- **第349行**: `ProviderManager.isPlannerCriticEnabled(context)`

### 7. Timeout 相关（应使用 TimeoutSettingsRepository）
- **第614行**: `ProviderManager.isStreamResumeEnabled(context)`
- **第619行**: `ProviderManager.getStreamResumeMaxRetries(context)`

---

## 三、CheckpointDetailActivity.kt - 4 处 ProviderManager 未迁移

### Provider 相关（应使用 ProviderRepository）
- **第226行**: `ProviderManager.getActiveConfig()`

### LLM Settings 相关（应使用 LlmSettingsRepository）
- **第228行**: `ProviderManager.getDefaultSystemPrompt(context)`

### Session 相关（应使用 SessionRepository）
- **第235行**: `ProviderManager.isAgentMode(context)`
- **第236行**: `ProviderManager.getWorkspacePath(context)`

---

## 四、TokenManagementActivity.kt - 2 处 ProviderManager 未迁移

### LLM Settings 相关（应使用 LlmSettingsRepository）
- **第60行**: `ProviderManager.getMaxTokens(context)`
- **第67行**: `ProviderManager.setMaxTokens(context, tokens)`

---

## 五、SamplingParamsConfigActivity.kt - 5 处 ProviderManager 未迁移

### Provider 相关（应使用 ProviderRepository）
- **第47行**: `ProviderManager.getActiveConfig()`

### LLM Settings 相关（应使用 LlmSettingsRepository）
- **第51行**: `ProviderManager.getTemperature(context)`
- **第55行**: `ProviderManager.getTopP(context)`
- **第59行**: `ProviderManager.getTopK(context)`
- **第69行**: `ProviderManager.setTemperature(context, tempValue)`
- **第70行**: `ProviderManager.setTopP(context, topPValue)`
- **第71行**: `ProviderManager.setTopK(context, topKValue)`

---

## 六、SystemPromptConfigActivity.kt - 2 处 ProviderManager 未迁移

### LLM Settings 相关（应使用 LlmSettingsRepository）
- **第49行**: `ProviderManager.getDefaultSystemPrompt(context)`
- **第56行**: `ProviderManager.setDefaultSystemPrompt(context, systemPrompt.trim())`

---

## 七、AppConfig 直接使用问题

### 1. AppNavigation.kt
- **第78行**: `AppConfig.checkpointService()`
  - 应该：通过 CheckpointRepository 访问

### 2. ChatViewModel.kt
- **第116行**: `AgentRuntimeBuilder(AppConfig.configRepo())`
- **第117行**: `ToolRegistryBuilder(AppConfig.configRepo())`
- **第880行**: `CompressionStrategyBuilder.build(AppConfig.configRepo(), contextManager)`
  - 应该：通过对应的 Repository 注入

### 3. McpServerEditActivity.kt
- **第78行**: `AppConfig.configRepo()`
  - 应该：使用注入的 McpRepository

### 4. McpServerActivity.kt
- **第108行**: `AppConfig.configRepo()`
- **第174行**: `AppConfig.mcpConnectionManager().preconnect()`
- **第305行**: `AppConfig.mcpConnectionManager().getServerStatus(server.name)`
- **第371行**: `AppConfig.mcpConnectionManager().getServerStatus(server.name)`
  - 应该：使用注入的 McpRepository

### 5. AgentStrategyActivity.kt
- **第57行**: `AppConfig.configRepo()`
  - 应该：使用注入的 AgentStrategyRepository

### 6. CheckpointRecovery.kt
- **第105行**: `AppConfig.checkpointService()`
  - 应该：使用注入的 CheckpointRepository

### 7. RepositoryModule.kt
- **所有 Repository 创建时**: 直接使用 `AppConfig.configRepo()` 等
  - 这是正确的，因为这是 DI 配置层

---

## 八、问题统计

| 文件 | ProviderManager 调用次数 | AppConfig 调用次数 | 总计 |
|------|------------------------|-------------------|------|
| ChatViewModel.kt | 32 | 3 | 35 |
| ChatExecutor.kt | 23 | 0 | 23 |
| CheckpointDetailActivity.kt | 4 | 0 | 4 |
| TokenManagementActivity.kt | 2 | 0 | 2 |
| SamplingParamsConfigActivity.kt | 5 | 0 | 5 |
| SystemPromptConfigActivity.kt | 2 | 0 | 2 |
| AppNavigation.kt | 0 | 1 | 1 |
| McpServerEditActivity.kt | 0 | 1 | 1 |
| McpServerActivity.kt | 0 | 4 | 4 |
| AgentStrategyActivity.kt | 0 | 1 | 1 |
| CheckpointRecovery.kt | 0 | 1 | 1 |
| **总计** | **68** | **11** | **79** |

---

## 九、修复优先级

### 高优先级（核心功能）
1. **ChatViewModel.kt** - 35 处，影响聊天核心功能
2. **ChatExecutor.kt** - 23 处，影响消息执行逻辑

### 中优先级（功能完整性）
3. **CheckpointDetailActivity.kt** - 4 处
4. **SamplingParamsConfigActivity.kt** - 5 处
5. **McpServerActivity.kt** - 4 处

### 低优先级（UI 配置页面）
6. **TokenManagementActivity.kt** - 2 处
7. **SystemPromptConfigActivity.kt** - 2 处
8. **AppNavigation.kt** - 1 处
9. **McpServerEditActivity.kt** - 1 处
10. **AgentStrategyActivity.kt** - 1 处
11. **CheckpointRecovery.kt** - 1 处

---

## 十、修复建议

### 1. ChatViewModel 修复方案
已注入的 Repository：
- ✅ SessionRepository
- ✅ ProviderRepository
- ✅ ModelSelectionRepository
- ✅ LlmSettingsRepository
- ✅ TimeoutSettingsRepository
- ✅ ToolSettingsRepository
- ✅ AgentStrategyRepository
- ✅ RagConfigRepository
- ✅ McpRepository
- ✅ CompressionSettingsRepository
- ✅ SnapshotSettingsRepository
- ✅ PlannerSettingsRepository

需要做的：
- 替换所有 `ProviderManager` 调用为对应 Repository 方法
- 移除 `ProviderManager.initialize(context)` 调用

### 2. ChatExecutor 修复方案
ChatExecutor 是 Service 类，不应该注入 Repository。

两种方案：
1. **方案A（推荐）**: 将所有配置作为参数传入 ChatExecutor
2. **方案B**: 创建 ChatExecutorConfig 数据类，封装所有配置

### 3. Activity 修复方案
为每个 Activity 注入对应的 Repository，替换 ProviderManager 调用。

---

## 十一、设计原则违反

根据 `docs/Repository抽离与功能域拆分方案.md`：

> Repository 只负责：
> - 配置读取/保存
> - 本地数据读写
> - 资源装载
> - 对底层 service/core 的数据型封装

> 页面以后只依赖对应功能域 Repository，不再直接调用 `ProviderManager`、`AppConfig` 或直接 new core `ConversationRepository`

**当前违反情况：**
- ❌ 大量页面仍直接调用 `ProviderManager`
- ❌ ChatExecutor 等 Service 类直接调用 `ProviderManager`
- ❌ 部分页面直接使用 `AppConfig.configRepo()`

---

## 十二、下一步行动

1. 完成 ChatViewModel 的 ProviderManager 替换（32 处）
2. 重构 ChatExecutor 的配置获取方式（23 处）
3. 修复其他 Activity 的 ProviderManager 调用（13 处）
4. 清理不必要的 AppConfig 直接调用（11 处）
5. 构建并测试完整迁移后的 APK
