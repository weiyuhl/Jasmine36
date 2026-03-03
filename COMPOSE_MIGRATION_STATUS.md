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

## 四、仍使用传统 XML 的布局（2 个）

### 1. activity_main.xml（在用）

- **路径**：`app/src/main/res/layout/activity_main.xml`
- **用途**：MainActivity 主布局
- **包含**：DrawerLayout、主内容区 ComposeView、右侧抽屉 ComposeView、左侧文件树（TextView + RecyclerView）

### 2. item_file_tree.xml（在用）

- **路径**：`app/src/main/res/layout/item_file_tree.xml`
- **用途**：FileTreeAdapter 的 RecyclerView 列表项
- **引用**：`app/src/main/java/com/lhzkml/jasmine/FileTreeAdapter.kt` 第 88 行

---

## 五、使用传统 View 的代码（完整清单）

以下按文件列出所有使用传统 Android View 的代码，并标明负责的功能与调用链。

### 1. MainActivity

| 路径 | 传统 View 组件 | 负责功能 | 说明 |
|-----|----------------|----------|------|
| `app/src/main/java/com/lhzkml/jasmine/MainActivity.kt` | `setContentView`、`findViewById`、`DrawerLayout`、`LinearLayout`、`TextView`、`RecyclerView`、`ComposeView` | 主界面布局、左右抽屉、文件树 | 第 42 行 `setContentView(R.layout.activity_main)`；第 46-50 行 findViewById；第 57、64 行 ComposeView；第 79 行 `AlertDialog.Builder` 删除对话确认 |
| 同上 | `AlertDialog.Builder` | 右侧抽屉中删除对话的确认对话框 | 第 79-84 行 |

### 2. FileTreeAdapter

| 路径 | 传统 View 组件 | 负责功能 | 说明 |
|-----|----------------|----------|------|
| `app/src/main/java/com/lhzkml/jasmine/FileTreeAdapter.kt` | `RecyclerView.Adapter`、`LayoutInflater.inflate`、`TextView`、`View.findViewById` | Agent 模式左侧文件树列表 | 第 14 行 extends RecyclerView.Adapter；第 88 行 inflate(R.layout.item_file_tree)；第 161-163 行 tvIndicator、tvIcon、tvName |

### 3. DialogHandlers（Tool 工具交互）

| 路径 | 传统 View 组件 | 负责功能 | 调用链 |
|-----|----------------|----------|--------|
| `app/src/main/java/com/lhzkml/jasmine/DialogHandlers.kt` | `AlertDialog.Builder`、`EditText` | **shellConfirmationHandler**：Shell 命令执行确认 | 第 24-31 行；由 `ChatViewModel` 注册到 `ToolRegistryBuilder`，Agent 执行 Shell 时弹出 |
| 同上 | `AlertDialog.Builder`、`EditText` | **askUserHandler**：AI 单问题询问用户输入 | 第 38-56 行；Tool 调用 askUser 时弹出 |
| 同上 | `AlertDialog.Builder`、`setSingleChoiceItems` | **singleSelectHandler**：AI 单选列表 | 第 63-82 行；Tool 调用 singleSelect 时弹出 |
| 同上 | `AlertDialog.Builder`、`ListView`、`dialog.listView` | **multiSelectHandler**：AI 多选列表 | 第 88-110 行；Tool 调用 multiSelect 时弹出 |
| 同上 | `AlertDialog.Builder`、`ListView`、`ArrayAdapter`、`android.R.layout.simple_list_item_1` | **rankPrioritiesHandler**：AI 排序优先级（可拖拽排序） | 第 116-147 行；Tool 调用 rankPriorities 时弹出 |
| 同上 | `AlertDialog.Builder`、`LinearLayout`、`TextView`、`EditText`、`ScrollView` | **askMultipleQuestionsHandler**：AI 多问题批量输入 | 第 155-178 行；Tool 调用 askMultipleQuestions 时弹出 |

**调用入口**：`app/src/main/java/com/lhzkml/jasmine/ui/ChatViewModel.kt` 第 187 行 `DialogHandlers.register(activity, toolRegistryBuilder)`

### 4. CheckpointRecovery（检查点恢复）

| 路径 | 传统 View 组件 | 负责功能 | 调用链 |
|-----|----------------|----------|--------|
| `app/src/main/java/com/lhzkml/jasmine/CheckpointRecovery.kt` | `AlertDialog.Builder`、`setItems` | **tryOfferCheckpointRecovery**：执行失败后选择恢复到的检查点 | 第 55-64 行；由 `ChatExecutor` 在 Agent 执行异常时调用（ChatViewModel 第 570-571 行） |
| 同上 | `AlertDialog.Builder`、`setMessage`、`setPositiveButton` | **tryOfferStartupRecovery**：启动时检测到可恢复检查点的提示 | 第 129-145 行；由 `ChatViewModel.tryOfferStartupRecovery` 第 602 行在加载对话时调用 |

### 5. LauncherActivity

| 路径 | 传统 View 组件 | 负责功能 | 说明 |
|-----|----------------|----------|------|
| `app/src/main/java/com/lhzkml/jasmine/LauncherActivity.kt` | `AlertDialog.Builder` | 请求文件访问权限时的说明对话框 | 第 92-102 行；用户点击「选择工作区」且未授权时弹出 |

### 6. SnapshotConfigActivity

| 路径 | 传统 View 组件 | 负责功能 | 说明 |
|-----|----------------|----------|------|
| `app/src/main/java/com/lhzkml/jasmine/SnapshotConfigActivity.kt` | `AlertDialog.Builder` | **confirmClearCheckpoints**：内存模式下清除检查点提示 | 第 77-80 行；内存存储时点击「清除全部」 |
| 同上 | `AlertDialog.Builder` | **confirmClearCheckpoints**：文件模式下清除检查点确认 | 第 84-96 行；文件存储时点击「清除全部」 |

### 7. MessageBubbles（AI 消息 Markdown 渲染）

| 路径 | 传统 View 组件 | 负责功能 | 说明 |
|-----|----------------|----------|------|
| `app/src/main/java/com/lhzkml/jasmine/ui/MessageBubbles.kt` | `AndroidView`、`TextView` | **AiContentBlocks**：AI 回复的 Markdown + LaTeX 渲染 | 第 120-138 行；通过 `AndroidView` 嵌入 `TextView`，配合 `MarkdownRenderer` 和 `LinkMovementMethod` |

### 8. MarkdownRenderer

| 路径 | 传统 View 组件 | 负责功能 | 说明 |
|-----|----------------|----------|------|
| `app/src/main/java/com/lhzkml/jasmine/MarkdownRenderer.kt` | `TextView`（参数） | Markdown 转 Spannable、LaTeX 占位替换 | 第 35 行 `render(text, textView)`；第 83-84 行 `injectLatexSpans` 需要 `TextView` 的 `currentTextColor`、`textSize`；被 `MessageBubbles.kt` 调用 |

---

## 六、迁移建议

### 高优先级

1. **MainActivity 整体 Compose 化**
   - 将 DrawerLayout 改为 Compose 的 `ModalNavigationDrawer` 或自定义 `Drawer`
   - 将左侧文件树改为 Compose `LazyColumn`，删除 `FileTreeAdapter` 和 `item_file_tree.xml`  
   - 可参考 `CheckpointManagerActivity` 的列表实现

### 低优先级

2. **DialogHandlers 中的对话框**
   - 将 `AlertDialog` + `ListView`/`EditText` 改为 Compose `CustomAlertDialog` + `LazyColumn`/`CustomTextField` 等

3. **CheckpointRecovery、LauncherActivity、SnapshotConfigActivity 的 AlertDialog**
   - 改为 Compose `CustomAlertDialog`

4. **MessageBubbles 的 AndroidView + TextView**
   - 需配合 MarkdownRenderer；可考虑 Compose 版 Markdown 渲染（如 Compose Markdown 库）替代

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
└── item_file_tree.xml     # 文件树列表项（FileTreeAdapter）
```

### 已删除的未引用布局

- `item_chat_log.xml`（已删除）
- `item_model_picker.xml`（已删除）

### 已删除的 XML 布局（历史迁移）

- `activity_provider_config.xml`
- `fragment_provider_config.xml`
- `fragment_model_list.xml`
- `item_model_list.xml`
- `activity_checkpoint_manager.xml`
- `activity_checkpoint_detail.xml`
- `item_checkpoint.xml`
- `item_checkpoint_session.xml`
