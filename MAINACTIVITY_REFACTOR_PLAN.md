# MainActivity 重构计划

## 当前问题
MainActivity 接近 2000 行，包含了太多职责，违反了单一职责原则。

## 主要功能模块分析

### 1. UI 管理（约 300 行）
- 视图初始化和绑定
- 布局管理（DrawerLayout、ScrollView、RecyclerView）
- 按钮状态更新
- 模型选择器弹窗
- 文件树面板

### 2. 对话管理（约 400 行）
- 对话列表订阅和刷新
- 新建/加载对话
- 消息历史管理
- 对话持久化

### 3. 消息发送和流式输出（约 600 行）
- sendMessage() 核心逻辑
- Agent 模式执行
- 普通聊天模式
- 流式输出处理
- 错误处理和重试

### 4. 上下文压缩（约 200 行）
- 历史压缩策略
- Token 估算
- TLDR 生成

### 5. 快照和恢复（约 200 行）
- Checkpoint 创建
- 快照恢复对话框
- 历史重建

### 6. 工具和运行时管理（约 200 行）
- ToolRegistry 构建
- MCP 预连接
- Tracing/EventHandler/Persistence 构建
- SystemContext 刷新

### 7. 辅助功能（约 100 行）
- 格式化工具
- UI 工具方法
- 生命周期管理

## 重构方案

### 方案 1：按功能拆分为多个 Manager 类（推荐）

```
MainActivity (核心协调器，约 400 行)
├── ChatUIManager (UI 管理)
│   ├── 视图初始化
│   ├── 布局管理
│   ├── 按钮状态
│   └── 模型选择器
├── ConversationManager (对话管理)
│   ├── 对话列表
│   ├── 新建/加载对话
│   └── 消息历史
├── MessageSender (消息发送)
│   ├── sendMessage 核心逻辑
│   ├── Agent 模式执行
│   ├── 普通聊天模式
│   └── 流式输出处理
├── CompressionManager (上下文压缩)
│   ├── 压缩策略
│   └── TLDR 生成
├── SnapshotManager (快照恢复)
│   ├── Checkpoint 管理
│   └── 恢复对话框
└── AgentRuntimeManager (运行时管理)
    ├── ToolRegistry 构建
    ├── MCP 连接
    └── Tracing/Event/Persistence
```

### 方案 2：按层次拆分为 Presenter/ViewModel

使用 MVP 或 MVVM 模式，将业务逻辑从 Activity 中分离。

### 方案 3：使用 Fragment 拆分 UI

将不同的 UI 区域拆分为独立的 Fragment。

## 推荐实施步骤

### 第一阶段：拆分消息发送逻辑（优先级最高）
1. 创建 `MessageSender` 类
2. 将 `sendMessage()` 及相关方法迁移
3. 保持接口简单，通过回调与 MainActivity 通信

### 第二阶段：拆分对话管理
1. 创建 `ConversationManager` 类
2. 迁移对话列表、加载、保存逻辑
3. 使用 Flow 或 LiveData 通知 UI 更新

### 第三阶段：拆分 UI 管理
1. 创建 `ChatUIManager` 类
2. 迁移视图初始化、布局管理
3. 提供简洁的 UI 更新接口

### 第四阶段：拆分其他模块
1. `CompressionManager`
2. `SnapshotManager`
3. `AgentRuntimeManager`

## 预期效果

- MainActivity 从 ~2000 行减少到 ~400 行
- 每个 Manager 类 200-400 行
- 职责清晰，易于测试和维护
- 降低耦合度，提高代码复用性

## 注意事项

1. 保持向后兼容，逐步迁移
2. 每个阶段完成后运行完整测试
3. Manager 类之间避免直接依赖，通过 MainActivity 协调
4. 使用接口定义 Manager 与 Activity 的通信契约
