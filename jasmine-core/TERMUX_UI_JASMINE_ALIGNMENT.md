# Termux UI 架构与 Jasmine 应用层技术对照

本文档提炼 Termux 官方 UI 架构的技术关键点，并对照 Jasmine 应用层现状，为未来可能的集成提供参考。**不涉及实际引入 Termux UI**。

---

## 一、Termux UI 架构技术关键点

### 1. 分层架构：Activity + Service

| 层级 | Termux | 职责 |
|------|--------|------|
| UI 层 | TermuxActivity | 显示、交互、绑定 Service |
| 服务层 | TermuxService | 会话管理、后台任务、前台通知 |
| 会话层 | TerminalSession | PTY、Shell 进程、I/O |
| 渲染层 | TerminalEmulator | 终端字符渲染 |

**关键**：UI 与业务逻辑分离，Service 独立于 Activity 生命周期。

### 2. 客户端接口解耦

```
TerminalView (View)
    ← TermuxTerminalViewClient (输入/手势/按键)
TerminalSession (会话)
    ← TermuxTerminalSessionActivityClient (会话生命周期)
```

**关键**：通过接口而非直接依赖，View 与 Session 解耦，便于测试和替换实现。

### 3. 配置系统分层

| 类型 | 存储 | 用途 |
|------|------|------|
| TermuxAppSharedPreferences | SharedPreferences | 字体、工具栏、键盘等 UI 偏好 |
| termux.properties | 文件 | 额外按键、全屏、快捷键等 |
| colors.properties | 文件 | 终端配色方案 |
| font.ttf | 文件 | 自定义字体 |

**关键**：UI 配置与运行时配置分离，支持用户自定义。

### 4. 输入处理链

```
用户输入 → TerminalView → *Client 接口 → Session.write() → PTY
```

- 软键盘、硬件键盘、额外按键统一入口
- 修饰键（Ctrl/Alt/Shift/Fn）状态由 Client 维护
- 音量键可映射为虚拟按键

### 5. 会话管理

- 多会话：侧边栏 ListView 切换
- 前台服务：保持进程、显示通知（会话数、任务数、WakeLock）
- Bootstrap 首次安装：`TermuxInstaller.setupBootstrapIfNeeded`

### 6. 布局结构

- DrawerLayout：主内容 + 侧边栏
- 主内容：TerminalView + ViewPager（ExtraKeysView + EditText）
- 侧边栏：会话列表 + 操作按钮

---

## 二、底层与系统级技术关键点

### 1. PTY（伪终端）

终端会话通过 PTY（Pseudo-Terminal）与 shell 进程通信：

- **PTY 创建**：JNI 调用 `createSubprocess()` 创建 PTY master/slave 对
- **Master 端**：应用持有 master 端 fd，用于读写
- **Slave 端**：shell 进程将 slave 作为 stdin/stdout/stderr
- **双向通信**：
  - 用户输入 → TerminalView → Master → Slave → Shell
  - Shell 输出 → Slave → Master → TerminalEmulator → TerminalView

### 2. I/O 线程分离

三个独立线程，避免阻塞主线程：

| 线程 | 职责 |
|------|------|
| **InputReader** | 从 PTY master 读取 shell 输出 → 写入队列 → 通知主线程 |
| **OutputWriter** | 从队列读取用户输入 → 写入 PTY master |
| **Waiter** | 等待 shell 进程退出，获取退出码，通知主线程 |

### 3. 主线程与工作线程

- **主线程（UI）**：绘制、输入事件、UI 状态、通过 Handler 接收 I/O 消息
- **I/O 线程**：读写 PTY fd、处理队列、等待进程
- **通信**：I/O → 主线程用 Handler；主线程 → I/O 用线程安全队列

### 4. 终端模拟器（TerminalEmulator）

负责解析终端控制序列：

- **ANSI/VT100 转义序列**：颜色、光标、清屏等
- **屏幕缓冲区**：当前显示的字符矩阵
- **滚动缓冲区**：历史输出（transcript rows）
- **字符编码**：UTF-8，宽字符（CJK 占 2 列）
- **光标/样式**：块状/下划线/竖线，粗体/斜体/颜色

### 5. 前台服务（Foreground Service）

- **Android 8.0+**：后台服务会被快速杀死，必须用前台服务
- **持久通知**：必须显示，告知用户服务运行中
- **通知内容**：会话数、任务数、WakeLock 状态、操作按钮
- **生命周期**：有会话即保持前台

### 6. WakeLock（唤醒锁）

防止设备休眠导致后台任务中断的机制：

#### 6.1 WakeLock 类型

**PowerManager.WakeLock**：
- 类型：`PARTIAL_WAKE_LOCK`
- 作用：保持 CPU 运行，屏幕可以关闭
- 标签：`"termux:service-wakelock"`
- 获取方式：`PowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)`

**WifiManager.WifiLock**：
- 类型：`WIFI_MODE_FULL_HIGH_PERF`
- 作用：保持 Wi-Fi 连接，高性能模式
- 标签：`"termux"`
- 获取方式：`WifiManager.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, tag)`

#### 6.2 使用场景

- 长时间运行的脚本（编译、构建）
- 网络下载任务
- SSH 服务器保持连接
- 后台数据处理
- 任何需要持续 CPU 和网络的任务

#### 6.3 获取和释放机制

**获取 WakeLock**：
```java
@SuppressLint({"WakelockTimeout", "BatteryLife"})
private void actionAcquireWakeLock() {
    if (mWakeLock != null) {
        // 已持有，忽略
        return;
    }
    
    // 获取 PowerManager.WakeLock
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mWakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, 
        "termux:service-wakelock"
    );
    mWakeLock.acquire();  // 无超时，持续持有
    
    // 获取 WifiManager.WifiLock
    WifiManager wm = (WifiManager) getApplicationContext()
        .getSystemService(Context.WIFI_SERVICE);
    mWifiLock = wm.createWifiLock(
        WifiManager.WIFI_MODE_FULL_HIGH_PERF, 
        "termux"
    );
    mWifiLock.acquire();
    
    // 检查电池优化豁免
    if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) {
        PermissionUtils.requestDisableBatteryOptimizations(this);
    }
    
    updateNotification();  // 更新通知显示状态
}
```

**释放 WakeLock**：
```java
private void actionReleaseWakeLock(boolean updateNotification) {
    if (mWakeLock == null && mWifiLock == null) {
        // 未持有，忽略
        return;
    }
    
    // 释放 PowerManager.WakeLock
    if (mWakeLock != null) {
        mWakeLock.release();
        mWakeLock = null;
    }
    
    // 释放 WifiManager.WifiLock
    if (mWifiLock != null) {
        mWifiLock.release();
        mWifiLock = null;
    }
    
    if (updateNotification) {
        updateNotification();  // 更新通知显示状态
    }
}
```

#### 6.4 生命周期管理

**自动释放时机**：
- Service 销毁时（`onDestroy()`）
- 用户手动释放（通知栏按钮）
- 无会话且无任务时自动停止服务

**持有规则**：
- PowerManager.WakeLock 和 WifiManager.WifiLock 总是一起获取和释放
- 不设置超时，由用户或系统控制释放时机
- Service 销毁时必须释放，避免泄漏

#### 6.5 通知栏集成

**通知内容显示**：
```java
String notificationText = sessionCount + " session(s)";
if (taskCount > 0) {
    notificationText += ", " + taskCount + " task(s)";
}

final boolean wakeLockHeld = mWakeLock != null;
if (wakeLockHeld) {
    notificationText += " (wake lock held)";  // 显示持有状态
}
```

**通知优先级**：
```java
// 持有 WakeLock 时使用高优先级（耗电提醒）
int priority = (wakeLockHeld) 
    ? Notification.PRIORITY_HIGH 
    : Notification.PRIORITY_LOW;
```

**通知按钮**：
```java
// 动态切换按钮文本和图标
String newWakeAction = wakeLockHeld 
    ? ACTION_WAKE_UNLOCK   // "Release wakelock"
    : ACTION_WAKE_LOCK;    // "Acquire wakelock"

String actionTitle = res.getString(wakeLockHeld 
    ? R.string.notification_action_wake_unlock 
    : R.string.notification_action_wake_lock);

int actionIcon = wakeLockHeld 
    ? android.R.drawable.ic_lock_idle_lock   // 解锁图标
    : android.R.drawable.ic_lock_lock;       // 加锁图标

builder.addAction(actionIcon, actionTitle, pendingIntent);
```

#### 6.6 Intent 触发

**获取 WakeLock**：
```java
Intent intent = new Intent(context, TermuxService.class);
intent.setAction("com.termux.service_wake_lock");
context.startService(intent);
```

**释放 WakeLock**：
```java
Intent intent = new Intent(context, TermuxService.class);
intent.setAction("com.termux.service_wake_unlock");
context.startService(intent);
```

#### 6.7 电池优化豁免

**检查豁免状态**：
```java
public static boolean checkIfBatteryOptimizationsDisabled(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }
    return true;  // Android 6.0 以下无需检查
}
```

**请求豁免**：
```java
@SuppressLint("BatteryLife")
public static void requestDisableBatteryOptimizations(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
    }
}
```

**最佳实践**：
- 首次获取 WakeLock 时检查并请求豁免
- 在通知中明确显示 WakeLock 状态
- 提供用户手动控制的入口（通知按钮）
- 避免长期持有，影响电池续航

#### 6.8 权限要求

**AndroidManifest.xml**：
```xml
<!-- WakeLock 权限 -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- 电池优化豁免（可选，运行时请求） -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Wi-Fi 状态和锁 -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

#### 6.9 注意事项

**性能影响**：
- PARTIAL_WAKE_LOCK：防止 CPU 休眠，持续耗电
- WIFI_MODE_FULL_HIGH_PERF：保持 Wi-Fi 高性能，增加功耗
- 用户反馈：持有 WakeLock 时功耗约翻倍

**内存泄漏风险**：
- 必须在 Service.onDestroy() 中释放
- 使用 `@SuppressLint("Wakelock")` 抑制 Lint 警告
- 确保 mWakeLock 和 mWifiLock 成对释放

**用户体验**：
- 通知中明确显示 "(wake lock held)"
- 使用高优先级通知提醒用户耗电
- 提供一键释放按钮
- 无会话和任务时自动释放并停止服务

**Android 版本兼容**：
- PARTIAL_WAKE_LOCK：所有版本支持
- 电池优化豁免：Android 6.0+ (API 23)
- 通知渠道：Android 8.0+ (API 26)

#### 6.10 典型使用流程

1. **用户触发**：点击通知栏 "Acquire wakelock" 按钮
2. **Service 接收**：`onStartCommand()` 收到 `ACTION_WAKE_LOCK` Intent
3. **获取锁**：调用 `actionAcquireWakeLock()`
4. **检查豁免**：如未豁免，弹出系统设置页面请求
5. **更新通知**：显示 "(wake lock held)"，切换为 "Release wakelock" 按钮
6. **执行任务**：CPU 和 Wi-Fi 保持运行
7. **用户释放**：点击 "Release wakelock" 按钮
8. **Service 接收**：收到 `ACTION_WAKE_UNLOCK` Intent
9. **释放锁**：调用 `actionReleaseWakeLock(true)`
10. **更新通知**：移除 "(wake lock held)"，恢复低优先级

### 7. 电池优化豁免

- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**：请求豁免 Doze
- **用户授权**：需在系统设置中手动允许
- **必要性**：保证后台任务、WakeLock、定时任务不被杀
- **最佳实践**：首次获取 WakeLock 时提示申请

### 8. 会话管理

- **会话列表**：Service 维护所有 TerminalSession
- **当前会话**：跟踪用户当前查看的会话
- **切换/清理**：侧边栏切换、进程退出自动清理、用户手动关闭
- **Service 销毁**：清理所有会话

### 9. 权限要求

| 权限 | 用途 |
|------|------|
| FOREGROUND_SERVICE | 前台服务（Android 9+） |
| WAKE_LOCK | 唤醒锁 |
| INTERNET | apt 下载 |
| READ/WRITE_EXTERNAL_STORAGE | termux-setup-storage |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 电池优化豁免（可选） |

### 10. 性能考虑

- **主线程不阻塞**：所有 I/O 在独立线程
- **缓冲区**：通常 4KB 平衡性能与内存
- **队列容量**：限制大小防 OOM
- **绘制**：TerminalView 只重绘变化区域
- **会话数量**：建议限制同时运行数

### 11. 剪贴板集成

- **复制**：长按选择 → 选择手柄 → ClipboardManager 复制；支持 OSC 52 序列
- **粘贴**：从剪贴板读取 → 写入 PTY → 发送给 shell，需处理换行等特殊字符
- **选择模式**：左右手柄、上下文菜单（复制/粘贴/分享）

### 12. 持久化

- **Service 生命周期**：独立于 Activity，会话在后台持续
- **进程保活**：前台服务 + WakeLock + 电池优化豁免
- **会话恢复**：Activity 重建后重新绑定 Service，恢复状态与输出历史

### 13. 通知管理

- **通知渠道**（Android 8.0+）：专用渠道，重要性 LOW
- **内容**：标题、会话/任务数、WakeLock 状态
- **操作**：点击打开 Activity；按钮（新建会话、切换 WakeLock、退出）
- **持久**：ongoing，用户不可滑动关闭

---

## 三、Jasmine 应用层现状对照

### 1. 架构对比（高层）

| 维度 | Termux | Jasmine |
|------|--------|---------|
| UI 框架 | View + XML | **Compose** |
| 导航 | 单 Activity | **Single-Activity + Navigation Compose** ✅ |
| 服务层 | TermuxService | 无独立 Service（Chat 在 ViewModel） |
| 配置 | SharedPrefs + 文件 | **config 包**（EncryptedConfigRepository） ✅ |
| DI | 无 | **Koin** ✅ |

### 2. 已有能力映射

| Termux 概念 | Jasmine 对应 | 说明 |
|-------------|--------------|------|
| TermuxActivity 主界面 | MainActivity + MainScreen | 全 Compose，双侧抽屉 |
| 侧边栏 | DrawerContent + FileTreeComposable | 左侧文件树，右侧设置入口 |
| 配置仓库 | AppConfig + ProviderManager | 已迁移到 config 包，加密存储 |
| 路由/导航 | AppNavigation + Routes | 30+ 路由，无 Activity 跳转 |
| 对话框 | ToolDialogState + ToolDialogComposable | 状态驱动，无 Activity 引用 |
| 会话/对话 | ChatViewModel + chatItems | 单会话为主，可扩展多会话 |

### 3. 技术关键点对齐情况

| 关键点 | Termux 做法 | Jasmine 现状 | 对齐度 |
|--------|-------------|--------------|--------|
| **UI 与业务分离** | Activity 只做 UI，Service 管会话 | ViewModel 管 Chat 状态，ChatExecutor 管执行 | ✅ 已分离 |
| **接口解耦** | *Client 接口 | DialogHandlers 通过 MutableStateFlow 解耦 | ✅ 部分对齐 |
| **配置分层** | Prefs + 文件 | 统一 EncryptedConfigRepository | ⚠️ 无 UI/运行时分层 |
| **输入链** | View → Client → Session | ChatInputBar → ViewModel → ChatExecutor | ✅ 链式 |
| **多会话** | 侧边栏切换 | 单对话为主 | ⚠️ 可扩展 |
| **前台服务** | 有 | 无 | ❌ 暂无需求 |

### 4. 底层/系统级关键点对齐

| 关键点 | Termux | Jasmine | 对齐度 |
|--------|--------|---------|--------|
| **PTY** | JNI 创建 master/slave，双向通信 | 无（Chat 用 HTTP/Stream） | ❌ 终端特有 |
| **I/O 线程分离** | InputReader/OutputWriter/Waiter | ChatExecutor 在 IO 上处理 StreamProcessor，Channel 发送 StreamUpdate | ✅ 已优化 |
| **主/工作线程** | Handler + 队列 | 主线程仅 processStreamUpdate，IO 做处理 | ✅ 已分离 |
| **TerminalEmulator** | 解析 ANSI/VT100 | MarkdownRenderer（非终端协议） | ❌ 领域不同 |
| **前台服务** | TermuxService | 无 | ❌ 暂无 |
| **WakeLock** | 长时间任务防休眠 | ✅ 已实现 | ✅ 已对齐 |
| **电池优化豁免** | 请求豁免 Doze | 无 | ❌ 暂无 |
| **会话管理** | Service 维护多会话 | ViewModel + 单对话 | ⚠️ 可扩展 |
| **权限** | 存储/网络/WakeLock 等 | 已有存储、网络、WakeLock | ✅ 已对齐 |
| **性能** | 4KB 缓冲、队列限制、增量绘制 | LazyColumn、协程 | ✅ 各有策略 |
| **剪贴板** | 选择→复制、粘贴→PTY | 可复用系统剪贴板 | ⚠️ 无终端选择模式 |
| **持久化** | Service + 会话恢复 | ConversationRepository（Room） | ✅ 对话持久化 |
| **通知管理** | 前台通知、操作按钮 | 无 | ❌ 暂无 |

### 5. 未来若引入终端 UI 的衔接点

（以下为概念性对照，不实施）

| 衔接点 | 建议 |
|--------|------|
| **Service 层** | 若需后台执行 Shell，可新增 `TermuxShellService`，与 MainActivity 绑定 |
| **客户端接口** | 可定义 `TerminalViewClient`、`TerminalSessionClient`，与 ChatViewModel 的 ToolDialogState 模式类似 |
| **配置** | 终端相关配置可复用 config 包，或扩展 `termux.properties` 风格文件 |
| **导航** | 终端界面可作为新路由（如 `Routes.TERMINAL`）加入 AppNavigation |
| **布局** | 终端区域可嵌入 MainScreen 的某个 Composable，或作为独立 Screen |
| **PTY/I/O** | 需引入 terminal-emulator 或自实现，JNI 创建 PTY 和 I/O 线程 |
| **前台服务** | 新增 TermuxShellService，需 FOREGROUND_SERVICE、WAKE_LOCK 等权限 |
| **电池优化** | 首次获取 WakeLock 时提示申请 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS |
| **剪贴板** | 终端选择模式需实现选择手柄、OSC 52 支持 |
| **持久化** | Service 独立于 Activity，会话在后台持续 |

---

## 四、可直接借鉴的设计模式（不引入 Termux UI）

### 1. 客户端接口模式

Jasmine 的 `DialogHandlers` 已采用「状态驱动 + 接口」：

```kotlin
// 现有：ToolDialogState 驱动 UI
ChatUiState.toolDialog: ToolDialogState?

// 可借鉴：任何「View 需要回调业务」的场景，用接口而非直接引用
interface XxxClient {
    fun onEvent(...)
}
```

### 2. 配置分层

若后续有「UI 偏好 vs 运行时配置」区分需求：

- **UI 偏好**：字体、主题、工具栏显隐 → 可单独 Prefs 或 config 扩展
- **运行时配置**：API Key、模型等 → 继续用 EncryptedConfigRepository

### 3. 输入链统一

ChatInputBar 已形成：`用户输入 → Composable → ViewModel → ChatExecutor`。  
若增加「额外按键」类控件，可参考 Termux 的 `ExtraKeysView`：配置驱动、统一写入同一入口。

### 4. 前台服务模式

若将来需要「后台持续执行任务 + 通知」：

- 参考 Termux 的 `runStartForeground`、`buildNotification`
- 与现有 Koin DI 结合，Service 可注入 `TermuxEnvironment` 等

### 5. 线程与 I/O 模式

Jasmine 的 ChatExecutor 用协程 + Flow，与 Termux 的「InputReader/OutputWriter/Waiter」模式不同。  
若将来有「阻塞式 I/O」场景（如 PTY 读写），可借鉴：独立线程 + 队列 + Handler 通知主线程。

### 6. 持久化与恢复

Jasmine 已有 ConversationRepository（Room）做对话持久化。  
Termux 的「Service 独立于 Activity、会话恢复」模式，可类比：多会话时需在 Service 重建后恢复会话状态。

---

## 五、总结

| 项目 | 结论 |
|------|------|
| **Jasmine 应用层** | 已具备 Single-Activity、Compose、Koin、config 包等基础 |
| **Termux UI 关键点** | 含 PTY、I/O 线程、前台服务、WakeLock、电池豁免、主/工作线程、会话管理、权限、性能、TerminalEmulator、剪贴板、持久化、通知等 |
| **对齐度** | 高层架构（Compose、导航、配置）部分一致；底层（PTY、Service、WakeLock）暂无 |
| **可借鉴** | 客户端接口、配置分层、输入链、前台服务、线程/队列模式、持久化恢复 |
| **当前动作** | 无，仅作技术对照与规划参考 |

---

*文档用途：为后续可能的 Termux 终端 UI 集成提供架构对照。*

---

## 六、已实施的 I/O 与线程优化（2026-03-08）

基于第 2、3 点（I/O 线程分离、主线程与工作线程）对应用层做了优化：

### 改动概要

1. **ChatExecutor**
   - 新增 `streamUpdateChannel: SendChannel<StreamUpdate>?` 参数
   - 在 IO 上创建并持有 `StreamProcessor`，流式回调中直接处理并 `send(update)`，不再 `withContext(Main)`
   - 暴露 `getLogContent()`、`getBufferedText()` 供 savePartial 使用

2. **ChatStateManager**
   - 新增 `processStreamUpdate(StreamUpdate)`，接收 Channel 发来的更新
   - `startStreaming(useChannelMode)`：Channel 模式下不创建 streamProcessor
   - 用 `lastAppliedBlocks` 支持 Channel 模式下的 `getPartialContent`

3. **ChatViewModel**
   - 创建 `Channel<StreamUpdate>(UNLIMITED)`，在主线程 `consumeEach` 并调用 `processStreamUpdate`
   - 持有 `activeChatExecutor`，供 savePartial 获取 logContent

### 效果

- **I/O 线程**：StreamProcessor 在 IO 上处理 chunk/thinking/toolCall 等
- **主线程**：只负责 `processStreamUpdate` 更新 UI
- **通信**：Channel 替代大量 `withContext(Dispatchers.Main)`，减少上下文切换

---

## 七、WakeLock 实现（2026-03-08）

基于第 6 点（WakeLock - 长时间任务防休眠）在应用层实现了完整的 WakeLock 功能：

### 实现组件

1. **WakeLockManager**
   - 位置：`app/src/main/java/com/lhzkml/jasmine/wakelock/WakeLockManager.kt`
   - 功能：
     - 管理 PowerManager.WakeLock（PARTIAL_WAKE_LOCK）
     - 管理 WifiManager.WifiLock（WIFI_MODE_FULL_HIGH_PERF）
     - 提供获取、释放、切换接口
     - 状态监听器机制

2. **BatteryOptimizationHelper**
   - 位置：`app/src/main/java/com/lhzkml/jasmine/wakelock/BatteryOptimizationHelper.kt`
   - 功能：
     - 检查电池优化豁免状态
     - 请求电池优化豁免
     - 打开电池优化设置页面

3. **UI 集成**
   - TopBar 显示 WakeLock 状态图标（🔓/🔒）
   - 点击图标切换 WakeLock 状态
   - 首次使用提示电池优化豁免

4. **ViewModel 集成**
   - ChatViewModel 中添加 WakeLockManager 实例
   - 通过 ChatUiState 管理状态
   - 通过 ChatUiEvent 处理事件
   - onCleared() 中自动释放

5. **权限配置**
   - AndroidManifest.xml 添加必要权限：
     - WAKE_LOCK
     - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     - ACCESS_WIFI_STATE
     - CHANGE_WIFI_STATE

### 使用方式

- 用户点击顶部栏锁图标切换 WakeLock 状态
- 🔒 = 未持有（点击获取）
- 🔓 = 已持有（点击释放）
- 首次使用自动提示豁免电池优化

### 对比 Termux

| 特性 | Termux | Jasmine |
|------|--------|---------|
| 触发方式 | 通知栏按钮 + Intent | 顶部栏图标 + 事件 |
| 状态显示 | 通知文本 "(wake lock held)" | 图标 🔓/🔒 |
| 架构 | Service 管理 | ViewModel 管理 |
| 生命周期 | Service 独立 | ViewModel 生命周期 |
| 实现状态 | ✅ 完整实现 | ✅ 完整实现 |

### 详细文档

- `app/WAKELOCK_IMPLEMENTATION.md` - 完整实现说明
