# MVVM 不符合项清单

**生成时间**: 2026-03-12  
**项目**: Jasmine36  
**分析范围**: `app/src/main/java/com/lhzkml/jasmine/`

---

## 一、概述

本项目当前仅完成了 Repository 层抽离与功能域拆分，但**应用层（UI 层）大部分页面未遵循 MVVM 架构**。

### MVVM 架构标准

符合 MVVM 的页面应具备以下特征：

1. **View (Activity/Composable)**: 仅负责 UI 渲染和用户事件转发
2. **ViewModel**: 继承 `androidx.lifecycle.ViewModel`，持有 UI 状态 (`StateFlow`/`LiveData`)
3. **状态封装**: 使用 `UiState` 数据类封装页面状态
4. **事件封装**: 使用 `UiEvent` 密封类封装用户操作
5. **依赖注入**: ViewModel 通过 Koin 注入，不直接在 Activity 中注入 Repository

---

## 二、符合 MVVM 的页面

### 已完成改造的页面（7 个）

| 页面 | ViewModel | Koin 注册 | UiState | UiEvent | 状态 |
|------|-----------|-----------|---------|---------|------|
| MainActivity (聊天) | ChatViewModel | ✅ | ⚠️ 内联 | ✅ | 基本符合 |
| (对话管理) | ConversationViewModel | ❌ | ❌ | ❌ | 未注册 Koin |
| (模型选择) | ModelViewModel | ❌ | ⚠️ 部分 | ❌ | 未注册 Koin |
| AboutActivity | AboutViewModel | ✅ | ✅ | ✅ | ✅ 已完成 |
| OssLicensesListActivity | OssLicensesListViewModel | ✅ | ✅ | ✅ | ✅ 已完成 |
| OssLicensesDetailActivity | OssLicensesDetailViewModel | ✅ | ✅ | ✅ | ✅ 已完成 |
| RulesActivity | RulesViewModel | ✅ | ✅ | ✅ | ✅ 已完成 |
| MnnModelSettingsActivity | MnnModelSettingsViewModel | N/A | ✅ | ✅ | ✅ 已完成 (特殊) |

> **注意**: 
> - `ConversationViewModel` 和 `ModelViewModel` 虽已创建但**未注册到 Koin**，无法被其他组件注入使用。
> - `MnnModelSettingsViewModel` 需要 modelId 参数，使用 ViewModelProvider.Factory 创建，不通过 Koin 注册。

---

## 三、不符合 MVVM 的 Activity 清单（共 31 个）

### 3.1 设置与配置类（18 个）

| # | Activity | 直接注入的 Repository | 问题描述 | 优先级 |
|---|----------|----------------------|----------|--------|
| 1 | `SettingsActivity` | 14+ 个 Repository | 直接在 Composable 中管理 `toolsEnabled`、`refreshTrigger` 等状态 | P0 |
| 2 | `ProviderConfigActivity` | ProviderRepository | Composable 中管理 `apiKey`、`baseUrl`、`vertexEnabled` 等状态 | P0 |
| 3 | `ProviderListActivity` | ProviderRepository | Composable 中直接读取和修改 Provider 列表 | P0 |
| 4 | `AddCustomProviderActivity` | ProviderRepository | 直接在 Composable 中处理表单提交 | P2 |
| 5 | `SamplingParamsConfigActivity` | LlmSettingsRepository | 无 ViewModel | P2 |
| 6 | `TokenManagementActivity` | LlmSettingsRepository | 无 ViewModel | P2 |
| 7 | `SystemPromptConfigActivity` | LlmSettingsRepository | 无 ViewModel | P2 |
| 8 | `ToolConfigActivity` | ToolSettingsRepository | Composable 中直接操作 Repository | P2 |
| 9 | `AgentStrategyActivity` | AgentStrategyRepository | 无 ViewModel | P2 |
| 10 | `ShellPolicyActivity` | ShellPolicyRepository | Composable 中直接操作 Repository | P2 |
| 11 | `TraceConfigActivity` | TraceSettingsRepository | Composable 中直接操作 Repository | P2 |
| 12 | `PlannerConfigActivity` | PlannerSettingsRepository | 无 ViewModel | P2 |
| 13 | `SnapshotConfigActivity` | SnapshotSettingsRepository | Composable 中直接操作 Repository | P2 |
| 14 | `EventHandlerConfigActivity` | EventHandlerSettingsRepository | 无 ViewModel | P2 |
| 15 | `CompressionConfigActivity` | CompressionSettingsRepository | Composable 中直接操作 Repository | P2 |
| 16 | `TimeoutConfigActivity` | TimeoutSettingsRepository | 无 ViewModel | P2 |
| 17 | `RulesActivity` | RulesRepository, SessionRepository | 无 ViewModel | P3 |
| 18 | `AboutActivity` | 无 | 纯 UI 页面，可接受 | P3 |

### 3.2 MNN 模型管理类（3 个）

| # | Activity | 直接注入的 Repository | 问题描述 | 优先级 |
|---|----------|----------------------|----------|--------|
| 19 | `MnnManagementActivity` | MnnModelRepository | Composable 中管理 `models`、`showDeleteDialog`、`progressState` 等状态 | P1 |
| 20 | `MnnModelMarketActivity` | 无 | 直接在 Activity 中处理网络请求和状态 | P2 |
| 21 | `MnnModelSettingsActivity` | 无 | 无 ViewModel | P3 |

### 3.3 RAG 配置类（3 个）

| # | Activity | 直接注入的 Repository | 问题描述 | 优先级 |
|---|----------|----------------------|----------|--------|
| 22 | `RagConfigActivity` | RagConfigRepository | Composable 中直接操作 Repository | P2 |
| 23 | `EmbeddingConfigActivity` | RagConfigRepository | Composable 中直接操作 Repository | P2 |
| 24 | `RagLibraryContentActivity` | RagLibraryRepository | Composable 中直接操作 Repository | P2 |

### 3.4 MCP 配置类（2 个）

| # | Activity | 直接注入的 Repository | 问题描述 | 优先级 |
|---|----------|----------------------|----------|--------|
| 25 | `McpServerActivity` | McpRepository | Composable 中直接操作 Repository | P2 |
| 26 | `McpServerEditActivity` | McpRepository | Composable 中直接操作 Repository | P2 |

### 3.5 OSS 许可类（2 个）✅ 已改造

| # | Activity | 直接注入的 Repository | 问题描述 | 优先级 | 状态 |
|---|----------|----------------------|----------|--------|------|
| 27 | `OssLicensesListActivity` | 无 | 纯 UI 展示页面 | P3 | ✅ 已改造 |
| 28 | `OssLicensesDetailActivity` | 无 | 纯 UI 展示页面 | P3 | ✅ 已改造 |

### 3.6 其他（3 个）

| # | Activity | 直接注入的 Repository | 问题描述 | 优先级 | 状态 |
|---|----------|----------------------|----------|--------|------|
| 29 | `LauncherActivity` | SessionRepository | Activity 中直接注入 Repository | P2 | ❌ 待改造 |
| 30 | `CheckpointManagerActivity` | CheckpointRepository | Composable 中直接操作 Repository | P2 | ❌ 待改造 |
| 31 | `CheckpointDetailActivity` | CheckpointRepository | Composable 中直接操作 Repository | P2 | ❌ 待改造 |
| 32 | `RulesActivity` | RulesRepository, SessionRepository | 无 ViewModel | P3 | ✅ 已改造 |
| 33 | `MnnModelSettingsActivity` | 无 | 无 ViewModel | P3 | ✅ 已改造 |

---

## 四、典型问题代码示例

### 问题 1：Activity 直接注入 Repository

```kotlin
// ❌ 不符合 MVVM
class SettingsActivity : ComponentActivity() {
    private val toolSettingsRepository: ToolSettingsRepository by inject()
    private val ragConfigRepository: RagConfigRepository by inject()
    // ... 14+ 个 Repository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        setContent {
            SettingsScreen(
                toolSettingsRepository = toolSettingsRepository,
                ragConfigRepository = ragConfigRepository,
                // ... 直接传递 Repository
            )
        }
    }
}
```

### 问题 2：Composable 中直接管理 UI 状态

```kotlin
// ❌ 不符合 MVVM
@Composable
fun SettingsScreen(toolSettingsRepository: ToolSettingsRepository) {
    // 状态直接在 Composable 中管理
    var toolsEnabled by remember { 
        mutableStateOf(toolSettingsRepository.isToolsEnabled()) 
    }
    
    // 业务逻辑直接在 Composable 中执行
    CustomSwitch(
        checked = toolsEnabled,
        onCheckedChange = { checked ->
            toolsEnabled = checked
            toolSettingsRepository.setToolsEnabled(checked)  // ❌
        }
    )
}
```

### 问题 3：Composable 中监听生命周期刷新状态

```kotlin
// ❌ 不符合 MVVM
@Composable
fun SettingsScreen(...) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 在 Composable 中监听生命周期
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()  // ❌ 应该在 ViewModel 中处理
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
```

---

## 五、符合 MVVM 的标准模板

### 推荐的 ViewModel 结构

```kotlin
// ✅ 符合 MVVM
class SettingsViewModel(
    private val toolSettingsRepository: ToolSettingsRepository,
    private val ragConfigRepository: RagConfigRepository,
    // ... 其他 Repository
) : ViewModel() {
    
    // UI 状态
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // 初始化
    init {
        loadSettings()
    }
    
    // 加载数据
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    toolsEnabled = toolSettingsRepository.isToolsEnabled()
                ) 
            }
        }
    }
    
    // 处理事件
    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.ToggleTools -> toggleTools(event.enabled)
            // ...
        }
    }
    
    private fun toggleTools(enabled: Boolean) {
        viewModelScope.launch {
            toolSettingsRepository.setToolsEnabled(enabled)
            _uiState.update { it.copy(toolsEnabled = enabled) }
        }
    }
}

// UI 状态数据类
data class SettingsUiState(
    val toolsEnabled: Boolean = false,
    val isLoading: Boolean = false,
    // ...
)

// UI 事件密封类
sealed class SettingsUiEvent {
    data class ToggleTools(val enabled: Boolean) : SettingsUiEvent()
    // ...
}
```

### 推荐的 Composable 结构

```kotlin
// ✅ 符合 MVVM
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),  // 或 koinViewModel()
    onNavigate: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    SettingsScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigate = onNavigate
    )
}

@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onNavigate: (String) -> Unit
) {
    CustomSwitch(
        checked = state.toolsEnabled,
        onCheckedChange = { enabled ->
            onEvent(SettingsUiEvent.ToggleTools(enabled))  // ✅ 发送事件
        }
    )
}
```

---

## 六、改造优先级

### P0 - 核心页面（优先改造）

| 页面 | 原因 |
|------|------|
| SettingsActivity | 设置总入口，使用频率高 |
| ProviderConfigActivity | 核心配置，直接影响聊天功能 |
| ProviderListActivity | 供应商列表管理 |

### P1 - 重要功能页面

| 页面 | 原因 |
|------|------|
| MnnManagementActivity | MNN 模型管理 |
| RagConfigActivity | RAG 配置 |
| McpServerActivity | MCP 服务器管理 |

### P2 - 其他配置页面

所有 `*ConfigActivity` 结尾的配置页面

### P3 - 纯展示页面

| 页面 | 说明 |
|------|------|
| AboutActivity | 纯展示，可接受 |
| OssLicensesListActivity | 纯展示，可接受 |
| OssLicensesDetailActivity | 纯展示，可接受 |

---

## 七、需要创建的 ViewModel 清单

### 已完成的 ViewModel（P3 优先级）

| # | ViewModel 名称 | 对应 Activity | 状态 |
|---|---------------|--------------|------|
| 1 | AboutViewModel | AboutActivity | ✅ 已完成 |
| 2 | OssLicensesListViewModel | OssLicensesListActivity | ✅ 已完成 |
| 3 | OssLicensesDetailViewModel | OssLicensesDetailActivity | ✅ 已完成 |
| 4 | RulesViewModel | RulesActivity | ✅ 已完成 |
| 5 | MnnModelSettingsViewModel | MnnModelSettingsActivity | ✅ 已完成 |

### 待完成的 ViewModel

| # | ViewModel 名称 | 对应 Activity | 优先级 |
|---|---------------|--------------|--------|
| 1 | SettingsViewModel | SettingsActivity | P0 |
| 2 | ProviderListViewModel | ProviderListActivity | P0 |
| 3 | ProviderConfigViewModel | ProviderConfigActivity | P0 |
| 4 | MnnManagementViewModel | MnnManagementActivity | P1 |
| 5 | RagConfigViewModel | RagConfigActivity | P1 |
| 6 | McpServerViewModel | McpServerActivity | P1 |
| 7 | ToolConfigViewModel | ToolConfigActivity | P2 |
| 8 | AgentStrategyViewModel | AgentStrategyActivity | P2 |
| 9 | ShellPolicyViewModel | ShellPolicyActivity | P2 |
| 10 | TraceConfigViewModel | TraceConfigActivity | P2 |
| 11 | PlannerConfigViewModel | PlannerConfigActivity | P2 |
| 12 | SnapshotConfigViewModel | SnapshotConfigActivity | P2 |
| 13 | EventHandlerConfigViewModel | EventHandlerConfigActivity | P2 |
| 14 | CompressionConfigViewModel | CompressionConfigActivity | P2 |
| 15 | TimeoutConfigViewModel | TimeoutConfigActivity | P2 |
| 16 | SamplingParamsViewModel | SamplingParamsConfigActivity | P2 |
| 17 | TokenManagementViewModel | TokenManagementActivity | P2 |
| 18 | SystemPromptViewModel | SystemPromptConfigActivity | P2 |
| 19 | CheckpointManagerViewModel | CheckpointManagerActivity | P2 |
| 20 | CheckpointDetailViewModel | CheckpointDetailActivity | P2 |
| 21 | MnnModelMarketViewModel | MnnModelMarketActivity | P2 |
| 22 | EmbeddingConfigViewModel | EmbeddingConfigActivity | P2 |
| 23 | RagLibraryViewModel | RagLibraryContentActivity | P2 |
| 24 | McpServerEditViewModel | McpServerEditActivity | P2 |
| 25 | AddCustomProviderViewModel | AddCustomProviderActivity | P2 |
| 26 | LauncherViewModel | LauncherActivity | P2 |

---

## 八、其他问题

### 8.1 Koin 配置不完整

当前 `ViewModelModule.kt` 只注册了 `ChatViewModel`：

```kotlin
val viewModelModule = module {
    viewModel { ChatViewModel(...) }  // 仅此一个
}
```

**需要补充**:
- `ConversationViewModel`
- `ModelViewModel`
- 所有新创建的 ViewModel

### 8.2 ChatViewModel 职责过重

当前 `ChatViewModel` 承担了过多职责：
- 会话管理
- 模型选择
- Provider 切换
- ToolRegistry 构建
- RAG/MCP/Trace/Snapshot 接线
- 执行调度
- 流式消息协调

**建议**: 将会话管理和模型选择职责分别委托给 `ConversationViewModel` 和 `ModelViewModel`

---

## 九、改造建议

### 第一步：统一导航入口
- 确保所有页面通过 `AppNavigation` 路由

### 第二步：改造 SettingsActivity
- 创建 `SettingsViewModel`、`SettingsUiState`、`SettingsUiEvent`
- 将 Repository 访问逻辑移入 ViewModel

### 第三步：改造 Provider 相关页面
- `ProviderListViewModel`
- `ProviderConfigViewModel`

### 第四步：分域改造其他配置页面
- RAG 域、MNN 域、MCP 域、Agent 域等

### 第五步：完善 Koin 配置
- 注册所有 ViewModel

### 第六步：拆分 ChatViewModel
- 将辅助职责委托给其他 ViewModel

---

## 十、验收标准

改造完成后，每个页面应满足：

- [ ] 有对应的 `ViewModel` 类
- [ ] ViewModel 注册到 Koin
- [ ] 有 `UiState` 数据类封装状态
- [ ] 有 `UiEvent` 密封类封装事件
- [ ] Composable 不直接访问 Repository
- [ ] Composable 不直接管理业务状态
- [ ] 业务逻辑在 ViewModel 中执行
- [ ] Activity 仅负责创建 Composable 和传递参数

---

**文档结束**