# Jasmine UI 迁移状态文档

本文档记录 Jasmine 应用中 UI 实现方式的分布情况，包括使用传统 XML 布局和 Jetpack Compose 的文件统计。

## 📊 总体统计

### Activity 层面
- **使用 Compose**: 17 个
- **使用 XML**: 4 个（其中 MainActivity 是混合模式）
- **Compose 占比**: 约 81%
- **Material 3**: ✅ 所有 Compose 页面都使用 Material 3

### 总体文件（包括 Fragment 和 Item）
- **使用 Compose**: 17 个
- **使用 XML**: 12 个（4 Activity + 2 Fragment + 6 Item 布局）
- **Compose 占比**: 约 59%

### 技术版本
- **Compose BOM**: 2025.05.01（最新版本）
- **Material 3**: ✅ 完全支持
- **Kotlin**: 2.2.10
- **Compose Compiler**: 2.2.10

---

## ✅ 已迁移到 Jetpack Compose 的页面（17个）

### 1. LauncherActivity
- **功能**: 启动页面
- **路径**: `app/src/main/java/com/lhzkml/jasmine/LauncherActivity.kt`
- **状态**: ✅ 完全使用 Compose

### 2. SettingsActivity
- **功能**: 设置页面
- **路径**: `app/src/main/java/com/lhzkml/jasmine/SettingsActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **说明**: 包含各种配置入口

### 3. ProviderListActivity
- **功能**: 供应商列表
- **路径**: `app/src/main/java/com/lhzkml/jasmine/ProviderListActivity.kt`
- **状态**: ✅ 完全使用 Compose

### 4. McpServerActivity
- **功能**: MCP 服务器管理
- **路径**: `app/src/main/java/com/lhzkml/jasmine/McpServerActivity.kt`
- **状态**: ✅ 完全使用 Compose

### 5. McpServerEditActivity
- **功能**: MCP 服务器编辑/添加
- **路径**: `app/src/main/java/com/lhzkml/jasmine/McpServerEditActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **特点**: 使用 BasicTextField 输入框

### 6. ShellPolicyActivity
- **功能**: Shell 策略配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/ShellPolicyActivity.kt`
- **状态**: ✅ 完全使用 Compose

### 7. ToolConfigActivity
- **功能**: 工具配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/ToolConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose

### 8. AgentStrategyActivity
- **功能**: Agent 策略配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/AgentStrategyActivity.kt`
- **状态**: ✅ 完全使用 Compose

### 9. CompressionConfigActivity
- **功能**: 智能上下文压缩配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/CompressionConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **迁移时间**: 最近迁移

### 10. TraceConfigActivity
- **功能**: 执行追踪配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/TraceConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **迁移时间**: 最近迁移

### 11. PlannerConfigActivity
- **功能**: 任务规划配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/PlannerConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **迁移时间**: 最近迁移

### 12. SnapshotConfigActivity
- **功能**: 快照配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/SnapshotConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **迁移时间**: 最近迁移

### 13. EventHandlerConfigActivity
- **功能**: 事件处理器配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/EventHandlerConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **迁移时间**: 最近迁移

### 14. TimeoutConfigActivity
- **功能**: 超时与续传配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/TimeoutConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **特点**: 使用 BasicTextField 输入框
- **迁移时间**: 最近迁移

### 15. TokenManagementActivity
- **功能**: Token 管理（最大回复 Token + Token 用量统计）
- **路径**: `app/src/main/java/com/lhzkml/jasmine/TokenManagementActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **特点**: 从弹窗改为独立页面，使用 BasicTextField 输入框
- **迁移时间**: 最近迁移

### 16. SystemPromptConfigActivity
- **功能**: 系统提示词配置
- **路径**: `app/src/main/java/com/lhzkml/jasmine/SystemPromptConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **特点**: 从弹窗改为独立页面，使用 BasicTextField 多行输入框
- **迁移时间**: 最近迁移

### 17. SamplingParamsConfigActivity
- **功能**: 采样参数配置（Temperature, Top P, Top K）
- **路径**: `app/src/main/java/com/lhzkml/jasmine/SamplingParamsConfigActivity.kt`
- **状态**: ✅ 完全使用 Compose
- **特点**: 从进度条改为 BasicTextField 输入框
- **迁移时间**: 最近迁移

---

## ⚠️ 仍使用传统 XML 布局的文件（12个）

### Activity（4个）

#### 1. MainActivity
- **功能**: 主聊天界面
- **路径**: `app/src/main/java/com/lhzkml/jasmine/MainActivity.kt`
- **布局文件**: `app/src/main/res/layout/activity_main.xml`
- **状态**: ⚠️ XML + Compose 混合模式
- **说明**: 
  - 主布局使用 XML（DrawerLayout + RecyclerView）
  - 聊天内容和右侧抽屉使用 ComposeView 嵌入 Compose
  - 复杂的聊天 UI，使用 RecyclerView 显示消息列表

#### 2. CheckpointManagerActivity
- **功能**: 检查点管理
- **路径**: `app/src/main/java/com/lhzkml/jasmine/CheckpointManagerActivity.kt`
- **布局文件**: `app/src/main/res/layout/activity_checkpoint_manager.xml`
- **状态**: ⚠️ 完全使用 XML
- **说明**: 显示检查点列表，支持清除和查看详情

#### 3. CheckpointDetailActivity
- **功能**: 检查点详情
- **路径**: `app/src/main/java/com/lhzkml/jasmine/CheckpointDetailActivity.kt`
- **布局文件**: `app/src/main/res/layout/activity_checkpoint_detail.xml`
- **状态**: ⚠️ 完全使用 XML
- **说明**: 显示检查点的详细信息和消息列表

#### 4. ProviderConfigActivity
- **功能**: 供应商配置容器
- **路径**: `app/src/main/java/com/lhzkml/jasmine/ProviderConfigActivity.kt`
- **布局文件**: `app/src/main/res/layout/activity_provider_config.xml`
- **状态**: ⚠️ 使用 XML（仅作为 Fragment 容器）
- **说明**: 使用 ViewPager2 + TabLayout，实际内容在 Fragment 中

### Fragment（2个）

#### 5. ProviderConfigFragment
- **功能**: 供应商配置表单
- **路径**: `app/src/main/java/com/lhzkml/jasmine/ProviderConfigFragment.kt`
- **布局文件**: `app/src/main/res/layout/fragment_provider_config.xml`
- **状态**: ⚠️ 完全使用 XML
- **说明**: 
  - API 地址、API Key 配置
  - Vertex AI 配置
  - 余额查询功能

#### 6. ModelListFragment
- **功能**: 模型列表
- **路径**: `app/src/main/java/com/lhzkml/jasmine/ModelListFragment.kt`
- **布局文件**: `app/src/main/res/layout/fragment_model_list.xml`
- **状态**: ⚠️ 完全使用 XML
- **说明**: 显示和选择可用模型列表

### Item 布局（6个）

#### 7. item_chat_log.xml
- **功能**: 聊天日志列表项
- **路径**: `app/src/main/res/layout/item_chat_log.xml`
- **状态**: ⚠️ XML 布局
- **用途**: MainActivity 的 RecyclerView 项

#### 8. item_checkpoint_session.xml
- **功能**: 检查点会话列表项
- **路径**: `app/src/main/res/layout/item_checkpoint_session.xml`
- **状态**: ⚠️ XML 布局
- **用途**: CheckpointManagerActivity 的 RecyclerView 项

#### 9. item_checkpoint.xml
- **功能**: 检查点列表项
- **路径**: `app/src/main/res/layout/item_checkpoint.xml`
- **状态**: ⚠️ XML 布局
- **用途**: CheckpointDetailActivity 的 RecyclerView 项

#### 10. item_file_tree.xml
- **功能**: 文件树列表项
- **路径**: `app/src/main/res/layout/item_file_tree.xml`
- **状态**: ⚠️ XML 布局
- **用途**: 文件选择器的 RecyclerView 项

#### 11. item_model_list.xml
- **功能**: 模型列表项
- **路径**: `app/src/main/res/layout/item_model_list.xml`
- **状态**: ⚠️ XML 布局
- **用途**: ModelListFragment 的 RecyclerView 项

#### 12. item_model_picker.xml
- **功能**: 模型选择器列表项
- **路径**: `app/src/main/res/layout/item_model_picker.xml`
- **状态**: ⚠️ XML 布局
- **用途**: 模型选择对话框的 RecyclerView 项

---

## 🎯 迁移建议

### 高优先级（核心功能）
1. **MainActivity** - 主聊天界面
   - 复杂度：高
   - 影响范围：核心功能
   - 建议：逐步迁移，先保持混合模式

### 中优先级（功能页面）
2. **CheckpointManagerActivity** - 检查点管理
   - 复杂度：中
   - 影响范围：快照功能
   
3. **CheckpointDetailActivity** - 检查点详情
   - 复杂度：中
   - 影响范围：快照功能

4. **ProviderConfigFragment** - 供应商配置
   - 复杂度：中
   - 影响范围：供应商配置

5. **ModelListFragment** - 模型列表
   - 复杂度：中
   - 影响范围：模型选择

### 低优先级（Item 布局）
6-12. **各种 Item 布局**
   - 复杂度：低
   - 影响范围：列表显示
   - 建议：配合对应 Activity/Fragment 一起迁移

---

## 📝 迁移历史

### 最近完成的迁移（2024）
1. CompressionConfigActivity - 智能上下文压缩配置
2. TraceConfigActivity - 执行追踪配置
3. PlannerConfigActivity - 任务规划配置
4. SnapshotConfigActivity - 快照配置
5. EventHandlerConfigActivity - 事件处理器配置
6. TimeoutConfigActivity - 超时与续传配置
7. TokenManagementActivity - Token 管理（从弹窗改为独立页面）
8. SystemPromptConfigActivity - 系统提示词配置（从弹窗改为独立页面）
9. SamplingParamsConfigActivity - 采样参数配置（从进度条改为输入框）

### 迁移特点
- 所有新迁移的页面都使用 **BasicTextField** 输入框，样式与 MCP 页面一致
- 从弹窗改为独立页面，提供更好的用户体验
- 使用 **DisposableEffect** 在页面销毁时自动保存配置
- 统一的页面布局和样式
- **完全使用 Material 3 (Material You)** 设计规范
- 支持动态颜色和现代化 UI

---

## 🔧 技术栈

### Compose 页面使用的技术

#### Material 3 (Material You)
- **状态**: ✅ 所有 Compose 页面都使用 Material 3
- **版本**: Compose BOM 2025.05.01（最新版本）
- **依赖**: `androidx.compose.material3:material3`
- **特点**: 
  - 支持 Material You 动态颜色
  - 现代化的设计语言
  - 更好的可访问性支持

#### Compose 组件
- **BasicTextField** - 自定义样式的输入框
- **Switch** - 开关组件（带自定义颜色）
- **Button** - 按钮组件
- **TextButton** - 文本按钮
- **Surface** - 卡片容器
- **LazyColumn** - 列表组件
- **AlertDialog** - 对话框
- **HorizontalDivider** - 分隔线
- **MaterialTheme** - 主题系统
- **DisposableEffect** - 生命周期管理

### XML 页面使用的组件
- **RecyclerView** - 列表视图
- **DrawerLayout** - 抽屉布局
- **ViewPager2** - 页面滑动
- **TabLayout** - 标签页
- **EditText** - 输入框
- **TextView** - 文本显示
- **Material Components** - Material Design 组件库（非 Material 3）

---

## 📊 迁移进度图

```
总体进度: ████████████████░░░░ 59% (17/29)

Activity 进度: ████████████████████ 81% (17/21)

Fragment 进度: ░░░░░░░░░░░░░░░░░░░░ 0% (0/2)

Item 布局进度: ░░░░░░░░░░░░░░░░░░░░ 0% (0/6)
```

---

## 📅 更新日期

最后更新：2024年（根据最近的迁移活动）

---

## 📌 备注

- 大部分配置页面已成功迁移到 Compose
- 剩余的主要是复杂的列表界面和主聊天界面
- 所有新迁移的页面都遵循统一的设计规范
- 输入框样式统一使用 BasicTextField，与 MCP 页面保持一致
