# 单一MVVM改造与ViewModel拆分方案

## 文档目的

本文档记录一个明确结论：

> **如果项目统一改造成 MVVM，不是把所有逻辑和所有数据塞进同一个 ViewModel，而是把整个 app 的功能页面统一纳入 MVVM 规范，并按功能域拆分多个 ViewModel。**

本文档只回答三件事：

1. 哪些功能需要做成 MVVM
2. 哪些功能分别应该拆成哪些 ViewModel
3. 哪些类不应该改成 ViewModel，而应继续保留为 Model/Service/Repository/Core

## 一、总原则

如果项目统一成 MVVM，应该遵守下面的规则：

1. **所有功能页面都走 MVVM**
   - View：只负责显示与交互
   - ViewModel：负责状态、事件、异步任务、effect
   - Model：负责 Repository、Service、Manager、Core、JNI 等

2. **不是一个项目只有一个 ViewModel**
   - 正确做法是：**按页面 / 按功能域拆多个 ViewModel**

3. **不是所有文件都需要 ViewModel**
   - 纯 UI 组件不需要 ViewModel
   - Repository / Service / Manager / Builder / JNI 也不是 ViewModel

4. **不能把复杂业务继续全塞进一个 ChatViewModel**
   - 聊天主链虽然可以继续是 MVVM
   - 但要按职责拆分，而不是继续做超级大 ViewModel

## 二、哪些功能需要做成 MVVM

如果项目统一成 MVVM，下面这些功能域都应该纳入 MVVM：

1. 启动页 / 权限 / 工作区选择
2. 聊天主链
3. 会话列表与会话切换
4. 模型选择与 thinking 开关
5. 设置首页
6. Provider 列表 / Provider 配置 / 自定义 Provider
7. Sampling / Token / System Prompt / Rules
8. Agent 工具 / Planner / Trace / Snapshot / Compression / Timeout / Shell Policy
9. RAG 配置 / Embedding 配置 / 知识库内容
10. MCP 服务列表 / MCP 编辑
11. Checkpoint 列表 / Checkpoint 详情 / 恢复
12. MNN 本地模型管理 / 模型市场 / 模型设置
13. About / OSS Licenses 等信息页（可做轻量 MVVM）

也就是说：

> **凡是用户看得到、能交互、会加载、会保存、会跳转、会刷新状态的页面，都应该纳入 MVVM。**

## 三、建议的 ViewModel 拆分方案

下面是建议的拆分方式。

### 1. 应用入口与主壳

#### `LauncherViewModel`
对应：

- `LauncherActivity`
- `LauncherScreen`

职责：

- 普通聊天 / Agent 模式选择
- 工作区选择结果处理
- 权限相关状态
- 跳转主界面的 effect

#### `AppShellViewModel`（可选）
对应：

- `MainActivity`
- `AppNavigation`

职责：

- 全局级 toast / loading / 导航协作（如后续确有需要）

说明：

- 这个不是必须的
- 如果全局状态不多，可以先不建

### 2. 聊天主链

#### `ChatViewModel`
对应：

- `ChatScreen`
- `ChatMessageList`
- `ChatInputBar`

职责：

- 当前聊天页 UI 状态
- 输入框与发送逻辑入口
- 生成中 / 错误 / 停止生成等页面状态
- 页面级 effect（toast、滚动、弹窗等）

#### `ConversationListViewModel`
对应：

- `DrawerContent`
- 会话列表区域

职责：

- 会话列表加载
- 新建会话
- 切换会话
- 删除会话
- 当前选中会话状态

#### `ModelSelectorViewModel`
对应：

- 模型选择 UI
- provider / model / thinking 相关区域

职责：

- 当前 provider
- 当前模型
- 模型列表
- 本地 / 远程模型切换状态
- thinking 模式开关

#### `WorkspaceSessionViewModel`
对应：

- Agent 模式 / 工作区相关状态

职责：

- 是否 Agent 模式
- 当前工作区路径
- 关闭工作区
- 恢复工作区会话
- 返回启动页 effect

#### `ChatCheckpointRecoveryViewModel`（可选）
对应：

- 聊天启动时的 checkpoint 恢复流程

职责：

- 恢复检测
- 恢复提示
- 恢复结果状态

### 3. 设置首页

#### `SettingsViewModel`
对应：

- `SettingsActivity`
- `SettingsScreen`

职责：

- 设置首页摘要
- tools 开关
- 各功能域摘要文案
- 进入子设置页的导航 effect
- 设置页刷新

### 4. Provider 配置域

#### `ProviderListViewModel`
对应：

- `ProviderListActivity`

职责：

- Provider 列表
- 当前启用 Provider
- 配置摘要
- 跳转编辑页 / 新增页

#### `ProviderEditorViewModel`
对应：

- `ProviderConfigActivity`
- `AddCustomProviderActivity`

职责：

- provider 表单数据
- API key / baseUrl / model 等输入
- 表单校验
- 保存 / 更新 / 删除

### 5. 基础参数配置域

#### `SamplingParamsViewModel`
对应：

- `SamplingParamsConfigActivity`

职责：

- temperature
- topP
- topK
- 输入校验与保存

#### `TokenManagementViewModel`
对应：

- `TokenManagementActivity`

职责：

- max tokens
- token 相关配置与保存

#### `SystemPromptViewModel`
对应：

- `SystemPromptConfigActivity`

职责：

- 系统提示词加载、编辑、保存

#### `RulesViewModel`
对应：

- `RulesActivity`

职责：

- personal rules
- project rules
- 加载 / 编辑 / 保存

### 6. Agent / 工具 / 调试配置域

#### `ToolConfigViewModel`
对应：`ToolConfigActivity`

#### `AgentStrategyViewModel`
对应：`AgentStrategyActivity`

#### `PlannerConfigViewModel`
对应：`PlannerConfigActivity`

#### `TraceConfigViewModel`
对应：`TraceConfigActivity`

#### `SnapshotConfigViewModel`
对应：`SnapshotConfigActivity`

#### `EventHandlerConfigViewModel`
对应：`EventHandlerConfigActivity`

#### `CompressionConfigViewModel`
对应：`CompressionConfigActivity`

#### `TimeoutConfigViewModel`
对应：`TimeoutConfigActivity`

#### `ShellPolicyViewModel`
对应：`ShellPolicyActivity`

这组页面的共同职责是：

- 加载当前配置
- 编辑与校验
- 保存配置
- 生成摘要状态

说明：

- 不建议把这一整组做成一个超大的 `AdvancedSettingsViewModel`
- 应该按页面拆分

### 7. RAG 域

#### `RagConfigViewModel`
对应：`rag/RagConfigActivity`

职责：

- RAG 开关
- topK
- 活跃知识库
- 索引扩展名
- 手动入库
- 工作区索引触发
- 索引状态 / 错误状态

#### `EmbeddingConfigViewModel`
对应：`rag/EmbeddingConfigActivity`

职责：

- 本地 / 远程 embedding 模式
- baseUrl
- apiKey
- model
- modelPath
- 保存与校验

#### `RagLibraryContentViewModel`
对应：`rag/RagLibraryContentActivity`

职责：

- 知识库内容列表
- 文档加载
- 删除 / 刷新 / 查询状态

### 8. MCP 域

#### `McpServerListViewModel`
对应：`McpServerActivity`

职责：

- MCP 开关
- 服务列表
- 服务状态
- 测试连接 / 重连 / 清缓存
- 删除确认状态

#### `McpServerEditViewModel`
对应：`McpServerEditActivity`

职责：

- 单个 MCP server 表单
- 名称、地址、参数校验
- 保存与更新

### 9. Checkpoint 域

#### `CheckpointManagerViewModel`
对应：`CheckpointManagerActivity`

职责：

- session 列表
- checkpoint 列表
- 统计信息
- 删除 session
- 清空全部
- 进入详情

#### `CheckpointDetailViewModel`
对应：`CheckpointDetailActivity`

职责：

- 单个 checkpoint 详情
- 恢复
- 删除
- 差异与状态展示

### 10. MNN 本地模型域

#### `MnnManagementViewModel`
对应：`mnn/MnnManagementActivity`

职责：

- 本地模型列表
- 导入
- 导出
- 删除
- 导入导出进度
- 下载源摘要

#### `MnnModelMarketViewModel`
对应：`mnn/MnnModelMarketActivity`

职责：

- 市场模型列表
- 下载状态
- 下载进度
- 安装状态

#### `MnnModelSettingsViewModel`
对应：`mnn/MnnModelSettingsActivity`

职责：

- 当前本地模型设置
- 推理参数
- thinking 开关
- 模型选择状态

### 11. 简单信息页

#### `AboutViewModel`（可选）
对应：`AboutActivity`

#### `OssLicensesViewModel`（可选）
对应：

- `oss/OssLicensesListActivity`
- `oss/OssLicensesDetailActivity`

说明：

- 如果只是静态页面，可以不做 ViewModel
- 如果要统一风格，也可以做轻量 ViewModel

## 四、最小可行拆分版本

如果不想一开始拆得太细，建议至少先有下面这些核心 ViewModel：

1. `LauncherViewModel`
2. `ChatViewModel`
3. `ConversationListViewModel`
4. `ModelSelectorViewModel`
5. `SettingsViewModel`
6. `ProviderListViewModel`
7. `ProviderEditorViewModel`
8. `RagConfigViewModel`
9. `EmbeddingConfigViewModel`
10. `McpServerListViewModel`
11. `CheckpointManagerViewModel`
12. `CheckpointDetailViewModel`
13. `MnnManagementViewModel`
14. `MnnModelMarketViewModel`
15. `MnnModelSettingsViewModel`

其余简单页面再逐步补齐。

## 五、哪些不需要 ViewModel

下面这些通常不需要单独做 ViewModel，因为它们只是纯 UI 组件：

- `ChatInputBar`
- `ChatMessageList`
- `MessageBubbles`
- `DrawerContent`（如果仅做纯展示时；若承载会话状态则由 `ConversationListViewModel` 驱动）
- `ui/components/*`
  - `CustomButton`
  - `CustomDialog`
  - `CustomDropdownMenu`
  - `CustomTextField`
  - `WakeLockButton`
  - 等

这些组件应该：

- 只接收 state
- 只发回调
- 不直接操作 Repository / Service / Config

## 六、哪些不要误改成 ViewModel

下面这些类即使项目统一成 MVVM，也**不应该**改成 ViewModel，而应该继续留在 Model / Service / Repository / Core 层：

- `ConversationRepository`
- `ProviderManager`
- `AppConfig`
- `ChatExecutor`
- `RagStore`
- `MnnModelManager`
- `CheckpointService`
- `McpConnectionManager`
- `AgentRuntimeBuilder`
- `ToolRegistryBuilder`
- `ChatClientFactory`
- `ChatClient`
- JNI / C++（如 `mnn_jni.cpp`）

原因是：

- 它们本来就是数据访问、配置服务、执行器、运行时装配或底层能力
- 这些属于 MVVM 中的 **Model 层**
- 不是 ViewModel

## 七、最终结论

如果项目统一成 MVVM，正确形态应该是：

> **所有功能页面都用 MVVM 组织，但按功能域拆成多个 ViewModel；复杂业务放进 Model/Service/Repository/Core，而不是塞进一个总 ViewModel。**

换句话说：

- **统一的是架构规范**
- **不是统一成一个文件**
- **不是统一成一个超级大 ViewModel**
