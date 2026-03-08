# Jasmine WakeLock 实现说明

## 概述

已在 Jasmine 应用层实现 WakeLock 功能，防止设备休眠导致长时间任务中断。

## 实现组件

### 1. 核心管理类

#### WakeLockManager
- 位置：`app/src/main/java/com/lhzkml/jasmine/wakelock/WakeLockManager.kt`
- 功能：
  - 管理 PowerManager.WakeLock（PARTIAL_WAKE_LOCK）
  - 管理 WifiManager.WifiLock（WIFI_MODE_FULL_HIGH_PERF）
  - 提供获取、释放、切换接口
  - 状态监听器机制

#### BatteryOptimizationHelper
- 位置：`app/src/main/java/com/lhzkml/jasmine/wakelock/BatteryOptimizationHelper.kt`
- 功能：
  - 检查电池优化豁免状态
  - 请求电池优化豁免
  - 打开电池优化设置页面

### 2. UI 组件

#### WakeLockButton
- 位置：`app/src/main/java/com/lhzkml/jasmine/ui/components/WakeLockButton.kt`
- 功能：
  - WakeLock 控制按钮（可选使用）
  - WakeLock 状态指示器

#### TopBar 集成
- 位置：`app/src/main/java/com/lhzkml/jasmine/ui/ChatScreen.kt`
- 功能：
  - 顶部栏显示 WakeLock 状态图标
  - 点击图标切换 WakeLock 状态
  - 🔓 = 已持有，🔒 = 未持有

### 3. ViewModel 集成

#### ChatViewModel
- 位置：`app/src/main/java/com/lhzkml/jasmine/ui/ChatViewModel.kt`
- 改动：
  - 添加 `wakeLockManager` 实例
  - 添加 `wakeLockListener` 监听状态变化
  - 实现 `toggleWakeLock()` 方法
  - 实现 `requestBatteryOptimization()` 方法
  - `onCleared()` 中清理 WakeLock

#### ChatUiState
- 位置：`app/src/main/java/com/lhzkml/jasmine/ui/ChatUiState.kt`
- 新增字段：
  - `wakeLockHeld: Boolean` - WakeLock 持有状态
  - `showBatteryOptimizationDialog: Boolean` - 电池优化对话框显示状态

#### ChatUiEvent
- 新增事件：
  - `ToggleWakeLock` - 切换 WakeLock
  - `RequestBatteryOptimization` - 请求电池优化豁免

### 4. 权限配置

#### AndroidManifest.xml
- 位置：`app/src/main/AndroidManifest.xml`
- 新增权限：
  ```xml
  <uses-permission android:name="android.permission.WAKE_LOCK" />
  <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
  <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
  <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
  ```

## 使用方式

### 用户操作

1. **获取 WakeLock**：
   - 点击顶部栏的 🔒 图标
   - 首次使用会提示豁免电池优化
   - 图标变为 🔓，表示已持有

2. **释放 WakeLock**：
   - 点击顶部栏的 🔓 图标
   - 图标变为 🔒，表示已释放

3. **电池优化豁免**：
   - 首次获取 WakeLock 时自动提示
   - 点击"去设置"跳转到系统设置
   - 在设置中允许 Jasmine 豁免电池优化

### 代码调用

```kotlin
// 在 ViewModel 中
viewModel.onEvent(ChatUiEvent.ToggleWakeLock)

// 在 Compose UI 中
val uiState by viewModel.uiState.collectAsState()
if (uiState.wakeLockHeld) {
    // WakeLock 已持有
}
```

## 技术细节

### WakeLock 类型

1. **PowerManager.WakeLock**
   - 类型：`PARTIAL_WAKE_LOCK`
   - 作用：保持 CPU 运行，屏幕可关闭
   - 标签：`"jasmine:wakelock"`

2. **WifiManager.WifiLock**
   - 类型：`WIFI_MODE_FULL_HIGH_PERF`
   - 作用：保持 Wi-Fi 连接，高性能模式
   - 标签：`"jasmine"`

### 生命周期管理

- **获取时机**：用户手动点击图标
- **释放时机**：
  - 用户手动点击图标
  - ViewModel 销毁时（`onCleared()`）
- **持有规则**：PowerManager.WakeLock 和 WifiManager.WifiLock 总是一起获取和释放

### 电池优化豁免

- **检查时机**：首次获取 WakeLock 时
- **请求方式**：
  - Android 6.0+：弹出系统对话框
  - 用户需手动允许
- **降级处理**：如果无法弹出对话框，打开电池优化设置页面

## 使用场景

适用于以下场景：

1. **长时间 AI 对话**：防止生成过程中设备休眠
2. **Agent 模式任务**：保证工具调用和文件操作不中断
3. **网络请求**：保持 Wi-Fi 连接稳定
4. **后台处理**：数据处理、文件操作等

## 注意事项

### 性能影响

- **CPU 持续运行**：增加电池消耗
- **Wi-Fi 高性能模式**：进一步增加功耗
- **用户反馈**：持有 WakeLock 时功耗约翻倍

### 最佳实践

1. **按需使用**：仅在需要时获取，完成后立即释放
2. **用户控制**：提供明确的获取/释放入口
3. **状态提示**：在 UI 中清晰显示 WakeLock 状态
4. **电池优化**：首次使用时引导用户豁免

### 内存泄漏防护

- ViewModel 销毁时自动释放
- 使用 `@SuppressLint("WakelockTimeout")` 抑制 Lint 警告
- 确保 PowerManager.WakeLock 和 WifiManager.WifiLock 成对释放

## 对比 Termux 实现

### 相同点

- 使用 PARTIAL_WAKE_LOCK 和 WIFI_MODE_FULL_HIGH_PERF
- 提供用户手动控制入口
- 检查并请求电池优化豁免
- 生命周期管理（销毁时释放）

### 不同点

| 特性 | Termux | Jasmine |
|------|--------|---------|
| 触发方式 | 通知栏按钮 + Intent | 顶部栏图标 + 事件 |
| 状态显示 | 通知文本 "(wake lock held)" | 图标 🔓/🔒 |
| 通知优先级 | 持有时 HIGH，否则 LOW | 无通知 |
| Service | TermuxService 前台服务 | 无 Service，ViewModel 管理 |
| 持久化 | Service 独立于 Activity | ViewModel 生命周期 |

### 架构差异

- **Termux**：Service 架构，WakeLock 在 Service 中管理，通过 Intent 控制
- **Jasmine**：ViewModel 架构，WakeLock 在 ViewModel 中管理，通过事件控制

## 未来扩展

### 可选功能

1. **自动获取**：长时间任务自动获取 WakeLock
2. **超时释放**：设置超时自动释放
3. **统计功能**：记录 WakeLock 持有时长
4. **通知显示**：在通知栏显示 WakeLock 状态

### Service 架构

如需后台持续运行（类似 Termux），可考虑：

1. 创建 `JasmineService` 前台服务
2. WakeLock 管理迁移到 Service
3. 通过 Intent 控制 WakeLock
4. 显示前台通知

## 测试建议

### 功能测试

1. 点击图标获取 WakeLock
2. 锁屏后运行长时间任务
3. 验证任务不中断
4. 点击图标释放 WakeLock
5. 验证电池优化提示

### 性能测试

1. 对比持有/未持有 WakeLock 的电池消耗
2. 测试 Wi-Fi 连接稳定性
3. 验证内存泄漏（ViewModel 销毁后）

## 参考文档

- `jasmine-core/TERMUX_UI_JASMINE_ALIGNMENT.md` - Termux UI 技术对照
- `termux-app-master/app/src/main/java/com/termux/app/TermuxService.java` - Termux 源码实现

---

**实现日期**: 2026-03-08  
**版本**: 1.0  
**状态**: ✅ 已完成
