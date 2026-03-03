# Jasmine 应用 UI 迁移状态

本文档记录 Jasmine 应用中各页面/组件的 UI 实现方式：传统 XML 布局 vs Jetpack Compose。  
最后更新：2025-03

---

## 一、总体概览

| 类别 | 已迁移 Compose | 仍使用 XML | 混合模式 |
|-----|----------------|------------|----------|
| Activity | 20 | 0 | 1 (MainActivity) |
| Fragment | 0 | 0 | - |
| 列表项/布局 | - | 2 在用 | - |
|  orphan 布局 | - | 2 (未引用) | - |

**说明**：项目已移除 Material 3，Compose 页面统一使用自定义组件库（CustomButton、CustomText、CustomSwitch 等）。

---

## 二、已完全迁移到 Jetpack Compose 的页面（20 个）

| 序号 | Activity | 功能 | 路径 |
|-----|----------|------|------|
| 1 | LauncherActivity | 启动页（选择聊天/工作区） | `LauncherActivity.kt` |
| 2 | SettingsActivity | 设置主页面 | `SettingsActivity.kt` |
| 3 | ProviderListActivity | 供应商列表 | `ProviderListActivity.kt` |
| 4 | ProviderConfigActivity | 供应商配置 + 获取模型列表（Tab） | `ProviderConfigActivity.kt` |
| 5 | McpServerActivity | MCP 服务器管理 | `McpServerActivity.kt` |
| 6 | McpServerEditActivity | MCP 服务器编辑/添加 | `McpServerEditActivity.kt` |
| 7 | ShellPolicyActivity | Shell 策略配置 | `ShellPolicyActivity.kt` |
| 8 | ToolConfigActivity | 工具配置 | `ToolConfigActivity.kt` |
| 9 | AgentStrategyActivity | Agent 策略配置 | `AgentStrategyActivity.kt` |
| 10 | CompressionConfigActivity | 智能上下文压缩配置 | `CompressionConfigActivity.kt` |
| 11 | TraceConfigActivity | 执行追踪配置 | `TraceConfigActivity.kt` |
| 12 | PlannerConfigActivity | 任务规划配置 | `PlannerConfigActivity.kt` |
| 13 | SnapshotConfigActivity | 快照配置 | `SnapshotConfigActivity.kt` |
| 14 | EventHandlerConfigActivity | 事件处理器配置 | `EventHandlerConfigActivity.kt` |
| 15 | TimeoutConfigActivity | 超时与续传配置 | `TimeoutConfigActivity.kt` |
| 16 | TokenManagementActivity | Token 管理 | `TokenManagementActivity.kt` |
| 17 | SystemPromptConfigActivity | 系统提示词配置 | `SystemPromptConfigActivity.kt` |
| 18 | SamplingParamsConfigActivity | 采样参数配置 | `SamplingParamsConfigActivity.kt` |
| 19 | CheckpointManagerActivity | 检查点管理 | `CheckpointManagerActivity.kt` |
| 20 | CheckpointDetailActivity | 检查点详情 | `CheckpointDetailActivity.kt` |

### Compose UI 组件（聊天界面）

| 组件 | 功能 | 路径 |
|-----|------|------|
| ChatScreen | 主聊天界面 | `ui/ChatScreen.kt` |
| ChatMessageList | 消息列表（LazyColumn） | `ui/ChatMessageList.kt` |
| ChatInputBar | 输入栏 | `ui/ChatInputBar.kt` |
| 消息气泡 | MessageBubbles | `ui/MessageBubbles.kt` |
| RightDrawerContent / DrawerContent | 右侧历史抽屉 | `ui/DrawerContent.kt` |

---

## 三、混合模式（XML + Compose）

### MainActivity

| 项目 | 实现方式 |
|-----|----------|
| 主布局结构 | XML (`activity_main.xml`)：DrawerLayout + LinearLayout |
| 左侧文件树 | XML：TextView + RecyclerView |
| 主内容区 | ComposeView 嵌入 Compose（ChatScreen） |
| 右侧抽屉 | ComposeView 嵌入 Compose（RightDrawerContent） |

**依赖的 XML 布局**：
- `activity_main.xml`：整体布局
- `item_file_tree.xml`：文件树列表项（FileTreeAdapter）

**依赖的 View 类**：
- `FileTreeAdapter.kt`：RecyclerView.Adapter，使用 `item_file_tree.xml`

---

## 四、仍使用传统 XML 的布局（4 个）

### 1. activity_main.xml（在用）

- **路径**：`app/src/main/res/layout/activity_main.xml`
- **用途**：MainActivity 主布局
- **包含**：DrawerLayout、主内容区 ComposeView、右侧抽屉 ComposeView、左侧文件树（TextView + RecyclerView）

### 2. item_file_tree.xml（在用）

- **路径**：`app/src/main/res/layout/item_file_tree.xml`
- **用途**：FileTreeAdapter 的 RecyclerView 列表项
- **引用**：`FileTreeAdapter.kt` 第 88 行

### 3. item_chat_log.xml（未引用）

- **路径**：`app/src/main/res/layout/item_chat_log.xml`
- **状态**：未在代码中引用，可能为历史遗留
- **建议**：可删除

### 4. item_model_picker.xml（未引用）

- **路径**：`app/src/main/res/layout/item_model_picker.xml`
- **状态**：未在代码中引用，可能为历史遗留
- **建议**：可删除

---

## 五、其他使用传统 View 的代码

### DialogHandlers.kt

- **用途**：`rankPrioritiesHandler` 等对话框
- **实现**：`AlertDialog.Builder` + `ListView` + `android.R.layout.simple_list_item_1`
- **位置**：`DialogHandlers.kt` 约 118 行

---

## 六、迁移建议

### 高优先级

1. **MainActivity 整体 Compose 化**
   - 将 DrawerLayout 改为 Compose 的 `ModalNavigationDrawer` 或自定义 `Drawer`
   - 将左侧文件树改为 Compose `LazyColumn`，删除 `FileTreeAdapter` 和 `item_file_tree.xml`  
   - 可参考 `CheckpointManagerActivity` 的列表实现

### 低优先级

2. **DialogHandlers 中的对话框**
   - 将 `AlertDialog` + `ListView` 改为 Compose `CustomAlertDialog` + `LazyColumn` 等

3. **清理未使用布局**
   - 删除 `item_chat_log.xml`、`item_model_picker.xml`（确认无引用后）

---

## 七、自定义 Compose 组件库

位于 `app/src/main/java/com/lhzkml/jasmine/ui/components/`：

- `CustomButton.kt` / `CustomTextButton.kt`
- `CustomText.kt`
- `CustomSwitch.kt`
- `CustomCheckbox.kt` / `CustomRadioButton.kt`
- `CustomTextField.kt` / `CustomOutlinedTextField`
- `CustomDropdownMenu.kt` / `CustomDropdownMenuItem`
- `CustomDialog.kt` / `CustomAlertDialog`
- `CustomDivider.kt` / `CustomHorizontalDivider`
- `CustomIcon.kt`

---

## 八、文件清单

### 当前保留的 XML 布局

```
app/src/main/res/layout/
├── activity_main.xml      # MainActivity 主布局
├── item_file_tree.xml     # 文件树列表项
├── item_chat_log.xml      # 未引用
└── item_model_picker.xml  # 未引用
```

### 已删除的 XML 布局（历史迁移）

- `activity_provider_config.xml`
- `fragment_provider_config.xml`
- `fragment_model_list.xml`
- `item_model_list.xml`
- `activity_checkpoint_manager.xml`
- `activity_checkpoint_detail.xml`
- `item_checkpoint.xml`
- `item_checkpoint_session.xml`
