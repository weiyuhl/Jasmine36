# Repository 迁移未完成问题清单

> 文档创建时间：2026/3/12
> 基于方案：`Repository 抽离与功能域拆分方案.md`

## 概述

本文档记录 `app` 层 Repository 抽离与功能域拆分方案实施过程中发现的问题和未完成项。

---

## 问题清单

### 1. ProviderManager 仍被大量使用 ⚠️ 严重

**问题描述**：方案要求 Repository 是唯一的配置访问入口，但旧的 `ProviderManager` 门面类仍被广泛使用。

**影响文件**：
| 文件 | 使用次数 | 主要问题 |
|------|----------|----------|
| `SettingsActivity.kt` | ~40 处 | 大量使用 `ProviderManager` 获取设置摘要信息 |
| `ModelViewModel.kt` | ~15 处 | 使用 `ProviderManager` 获取/保存配置 |
| `MnnChatClient.kt` | ~4 处 | 使用 `ProviderManager.getMnnThinkingEnabled` |
| `DrawerContent.kt` | ~2 处 | 使用 `ProviderManager.getAllProviders()` |
| `LauncherActivity.kt` | ~1 处 | 使用 `ProviderManager.initialize()` |
| `ChatViewModel.kt` | 导入但未直接使用 | 残留导入语句 |

**修复方案**：
1. `SettingsActivity` → 注入对应的 Repository 获取数据
2. `ModelViewModel` → 使用 `ProviderRepository` + `ModelSelectionRepository`
3. `MnnChatClient` → 使用 `ModelSelectionRepository` 或 `MnnModelRepository`
4. `DrawerContent` → 使用 `ProviderRepository`
5. `LauncherActivity` → 移除 `ProviderManager.initialize()`（已在 `JasmineApplication` 初始化）

**优先级**：高

---

### 2. RepositoryModule 使用 AppConfig.configRepo() ⚠️ 中等

**问题描述**：`RepositoryModule.kt` 中大部分 Repository 使用 `AppConfig.configRepo()` 直接创建，而非通过 DI 注入 `ConfigRepository`。

**当前代码**：
```kotlin
single<TimeoutSettingsRepository> {
    DefaultTimeoutSettingsRepository(AppConfig.configRepo())
}
```

**问题**：
- 导致 Repository 难以进行单元测试
- 违反了依赖注入的原则
- 与方案中"依赖注入友好"的设计目标相悖

**修复方案**：
```kotlin
// 先定义 ConfigRepository 的注入
single<ConfigRepository> {
    AppConfig.configRepo()
}

// 然后使用注入
single<TimeoutSettingsRepository> {
    DefaultTimeoutSettingsRepository(get())
}
```

**优先级**：中

---

### 3. ChatViewModel 保留旧代码引用 ⚠️ 中等

**问题描述**：`ChatViewModel.kt` 中仍保留对旧 API 的直接引用。

**具体问题**：
1. `private val configRepo = AppConfig.configRepo()` - 直接引用
2. `import com.lhzkml.jasmine.config.ProviderManager` - 残留导入

**修复方案**：
- 移除 `configRepo` 字段，使用已注入的 Repository
- 移除未使用的 `ProviderManager` 导入

**优先级**：中

---

### 4. SettingsActivity 未使用 Repository ⚠️ 高

**问题描述**：`SettingsActivity.kt` 是设置汇总页，大量使用 `ProviderManager` 获取各功能域的状态摘要。

**当前使用方式**：
```kotlin
val toolsEnabled = ProviderManager.isToolsEnabled(context)
val ragTopK = ProviderManager.getRagTopK(context)
val mcpServers = ProviderManager.getMcpServers(context)
// ... 等等
```

**修复方案**：
注入对应的 Repository：
```kotlin
private val toolSettingsRepository: ToolSettingsRepository by inject()
private val ragConfigRepository: RagConfigRepository by inject()
private val mcpRepository: McpRepository by inject()
// ... 等等
```

**优先级**：高

---

### 5. ModelViewModel 未使用 Repository ⚠️ 高

**问题描述**：`ModelViewModel.kt` 使用 `ProviderManager` 获取和保存模型配置。

**当前使用方式**：
```kotlin
val activeId = ProviderManager.getActiveId()
val provider = ProviderManager.getProvider(activeId)
ProviderManager.saveConfig(context, activeId, key, baseUrl, model)
```

**修复方案**：
使用 `ProviderRepository` 和 `ModelSelectionRepository` 替代。

**优先级**：高

---

### 6. MnnChatClient 使用 ProviderManager ⚠️ 中

**问题描述**：`MnnChatClient.kt` 使用 `ProviderManager` 获取本地模型的 Thinking 配置。

**当前使用方式**：
```kotlin
val enableThinking = ProviderManager.getMnnThinkingEnabled(context, targetId)
ProviderManager.setMnnThinkingEnabled(context, modelId, thinking)
```

**修复方案**：
使用 `ModelSelectionRepository.isThinkingEnabled()` 和 `setThinkingEnabled()` 替代。

**优先级**：中

---

### 7. DrawerContent 使用 ProviderManager ⚠️ 低

**问题描述**：`DrawerContent.kt` 使用 `ProviderManager.getAllProviders()` 获取 Provider 列表显示名称。

**修复方案**：
使用 `ProviderRepository.getAllProviders()` 替代。

**优先级**：低

---

### 8. LauncherActivity 调用 ProviderManager.initialize() ⚠️ 低

**问题描述**：`LauncherActivity.kt` 中调用 `ProviderManager.initialize(this)`，但初始化已在 `JasmineApplication` 中完成。

**修复方案**：
移除 `LauncherActivity` 中的 `ProviderManager.initialize(this)` 调用。

**优先级**：低

---

## 修复状态

| 问题编号 | 问题描述 | 状态 | 修复日期 |
|----------|----------|------|----------|
| 1 | ProviderManager 仍被大量使用 | ⏳ 待修复 | - |
| 2 | RepositoryModule 使用 AppConfig.configRepo() | ⏳ 待修复 | - |
| 3 | ChatViewModel 保留旧代码引用 | ⏳ 待修复 | - |
| 4 | SettingsActivity 未使用 Repository | ⏳ 待修复 | - |
| 5 | ModelViewModel 未使用 Repository | ⏳ 待修复 | - |
| 6 | MnnChatClient 使用 ProviderManager | ⏳ 待修复 | - |
| 7 | DrawerContent 使用 ProviderManager | ⏳ 待修复 | - |
| 8 | LauncherActivity 调用 ProviderManager.initialize() | ⏳ 待修复 | - |

---

## 修复建议

### 阶段一：基础设施修复
1. 修复 `RepositoryModule.kt` 的 DI 配置
2. 清理 `ChatViewModel.kt` 中的旧代码

### 阶段二：核心页面迁移
3. 迁移 `SettingsActivity.kt` 使用 Repository
4. 迁移 `ModelViewModel.kt` 使用 Repository

### 阶段三：补充修复
5. 迁移 `MnnChatClient.kt` 使用 Repository
6. 迁移 `DrawerContent.kt` 使用 Repository
7. 修复 `LauncherActivity.kt`

### 阶段四：清理与验证
8. 将 `ProviderManager` 标记为 `@Deprecated`
9. 运行完整测试确保功能正常

---

## 注意事项

1. **不要删除 ProviderManager**：在完全确认所有调用都迁移之前，保留 `ProviderManager` 作为兼容层
2. **单元测试**：修复完成后，为关键 Repository 添加单元测试
3. **回归测试**：每次修改后运行完整构建和基础功能测试
4. **文档更新**：修复完成后更新 `Repository 实现进度.md`

---

## 相关文件

- 方案文档：`docs/Repository 抽离与功能域拆分方案.md`
- 实现进度：`docs/Repository 实现进度.md`
- 迁移总结：`docs/Repository 迁移完成总结.md`
- 注意事项：`docs/Repository 迁移注意事项.md`