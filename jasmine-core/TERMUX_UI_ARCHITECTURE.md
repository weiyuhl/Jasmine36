# Termux 官方应用 UI 层架构说明

本文档说明 Termux 官方应用的 UI 层是如何实现的，供 Jasmine-core 框架的使用者参考。

## 核心架构

Termux 应用采用经典的 Android Service + Activity 架构：

```
TermuxActivity (UI 层)
    ↓ 绑定
TermuxService (后台服务)
    ↓ 管理
TerminalSession (终端会话)
    ↓ 使用
TerminalEmulator (终端模拟器)
```

## 主要组件

### 1. TermuxActivity (主界面)

**职责**：
- 显示终端界面
- 处理用户交互（触摸、键盘输入）
- 管理 UI 组件（工具栏、侧边栏、额外按键）
- 绑定到 TermuxService

**核心成员变量**：
```java
// 服务连接
TermuxService mTermuxService;

// 终端视图
TerminalView mTerminalView;

// 客户端接口
TermuxTerminalViewClient mTermuxTerminalViewClient;
TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

// UI 组件
TermuxActivityRootView mTermuxActivityRootView;
ExtraKeysView mExtraKeysView;
TermuxSessionsListViewController mTermuxSessionListViewController;

// 配置
TermuxAppSharedPreferences mPreferences;
TermuxAppSharedProperties mProperties;
```

**生命周期**：
```java
onCreate() {
    // 1. 加载配置
    mProperties = TermuxAppSharedProperties.getProperties();
    mPreferences = TermuxAppSharedPreferences.build(this, true);
    
    // 2. 设置布局
    setContentView(R.layout.activity_termux);
    
    // 3. 初始化终端视图和客户端
    setTermuxTerminalViewAndClients();
    
    // 4. 启动并绑定 TermuxService
    Intent serviceIntent = new Intent(this, TermuxService.class);
    startService(serviceIntent);
    bindService(serviceIntent, this, 0);
}

onServiceConnected() {
    // 服务连接成功后
    mTermuxService = ((TermuxService.LocalBinder) service).service;
    
    // 如果没有会话，安装 Bootstrap
    if (mTermuxService.isTermuxSessionsEmpty()) {
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
            // 创建新会话
            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
        });
    }
}
```


### 2. TermuxService (后台服务)

**职责**：
- 管理所有终端会话（TermuxSession）
- 管理后台任务（AppShell）
- 显示前台通知
- 处理 WakeLock 和 WifiLock
- 执行插件命令

**核心成员变量**：
```java
// Shell 管理器
TermuxShellManager mShellManager;

// 客户端
TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
TermuxTerminalSessionServiceClient mTermuxTerminalSessionServiceClient;

// 锁
PowerManager.WakeLock mWakeLock;
WifiManager.WifiLock mWifiLock;
```

**关键方法**：
```java
// 创建终端会话
createTermuxSession(ExecutionCommand executionCommand);

// 创建后台任务
createTermuxTask(ExecutionCommand executionCommand);

// 处理服务执行命令
actionServiceExecute(Intent intent);

// 获取/释放 WakeLock
actionAcquireWakeLock();
actionReleaseWakeLock();
```

**前台服务**：
```java
private void runStartForeground() {
    setupNotificationChannel();
    startForeground(TERMUX_APP_NOTIFICATION_ID, buildNotification());
}

private Notification buildNotification() {
    // 构建通知，显示：
    // - 会话数量
    // - 后台任务数量
    // - WakeLock 状态
    // - 操作按钮（新建会话、退出）
}
```


### 3. TerminalView (终端视图)

**来源**：`terminal-view` 模块（独立库）

**职责**：
- 渲染终端内容
- 处理触摸事件
- 处理键盘输入
- 支持文本选择和复制

**关键特性**：
```java
// 文本渲染
onDraw(Canvas canvas);

// 触摸处理
onTouchEvent(MotionEvent event);

// 键盘输入
onKeyDown(int keyCode, KeyEvent event);

// 文本选择
startTextSelectionMode();
```

### 4. TerminalSession (终端会话)

**来源**：`terminal-emulator` 模块

**职责**：
- 管理 PTY（伪终端）
- 运行 shell 进程
- 处理输入输出
- 管理终端状态

**核心流程**：
```java
// 创建会话
TerminalSession.execute() {
    // 1. 创建 PTY
    int[] processId = new int[1];
    int ptyFd = JNI.createSubprocess(..., processId);
    
    // 2. 创建 TerminalEmulator
    mEmulator = new TerminalEmulator(...);
    
    // 3. 启动 I/O 线程
    new Thread(mInputReader).start();
    new Thread(mOutputWriter).start();
}
```


## UI 组件详解

### 1. 主布局 (activity_termux.xml)

```xml
<DrawerLayout>
    <!-- 主内容区 -->
    <TermuxActivityRootView>
        <RelativeLayout>
            <!-- 终端视图 -->
            <TerminalView />
            
            <!-- 工具栏 -->
            <ViewPager id="terminal_toolbar_view_pager">
                <!-- 额外按键 -->
                <ExtraKeysView />
                <!-- 文本输入 -->
                <EditText id="terminal_toolbar_text_input" />
            </ViewPager>
            
            <!-- 底部空白（用于调整布局） -->
            <View id="activity_termux_bottom_space_view" />
        </RelativeLayout>
    </TermuxActivityRootView>
    
    <!-- 侧边栏 -->
    <RelativeLayout>
        <!-- 会话列表 -->
        <ListView id="terminal_sessions_list" />
        
        <!-- 按钮 -->
        <LinearLayout>
            <ImageButton id="new_session_button" />
            <ImageButton id="toggle_keyboard_button" />
            <ImageButton id="settings_button" />
        </LinearLayout>
    </RelativeLayout>
</DrawerLayout>
```

### 2. 额外按键 (ExtraKeysView)

**功能**：在屏幕上显示额外的按键（ESC, CTRL, ALT, TAB 等）

**配置**：通过 `~/.termux/termux.properties` 配置
```properties
extra-keys = [['ESC','/','-','HOME','UP','END','PGUP'], \
              ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]
```

**实现**：
```java
class ExtraKeysView extends GridLayout {
    // 根据配置创建按钮
    void reload(ExtraKeysInfo extraKeysInfo);
    
    // 读取特殊按键状态
    Boolean readSpecialButton(SpecialButton button);
}
```


### 3. 会话列表 (TermuxSessionsListViewController)

**功能**：在侧边栏显示所有终端会话

**实现**：
```java
class TermuxSessionsListViewController implements 
    BaseAdapter, OnItemClickListener, OnItemLongClickListener {
    
    // 点击切换会话
    onItemClick() {
        mActivity.switchToSession(position);
    }
    
    // 长按显示菜单（重命名、关闭）
    onItemLongClick() {
        showSessionMenu(session);
    }
}
```

### 4. 上下文菜单

**触发**：长按终端或按菜单键

**选项**：
```java
onCreateContextMenu() {
    menu.add("Select URL");           // 选择 URL
    menu.add("Share transcript");     // 分享终端内容
    menu.add("Share selected text");  // 分享选中文本
    menu.add("Autofill username");    // 自动填充用户名
    menu.add("Autofill password");    // 自动填充密码
    menu.add("Reset terminal");       // 重置终端
    menu.add("Kill process");         // 杀死进程
    menu.add("Style terminal");       // 样式设置
    menu.add("Toggle keep screen on");// 保持屏幕常亮
    menu.add("Help");                 // 帮助
    menu.add("Settings");             // 设置
    menu.add("Report issue");         // 报告问题
}
```


## 客户端接口

### 1. TermuxTerminalViewClient

**职责**：处理 TerminalView 的事件

**关键方法**：
```java
// 缩放手势
float onScale(float scale);

// 单击
void onSingleTapUp(MotionEvent e);

// 按键处理
boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);
boolean onKeyUp(int keyCode, KeyEvent e);

// 读取修饰键状态
boolean readControlKey();
boolean readAltKey();
boolean readShiftKey();
boolean readFnKey();

// 处理字符输入
boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);

// 复制模式
void copyModeChanged(boolean copyMode);
```

**特殊功能**：
```java
// 音量键作为虚拟按键
handleVirtualKeys() {
    // 音量下 = CTRL
    // 音量上 = FN
}

// Fn 键组合
onCodePoint() {
    if (mVirtualFnKeyDown) {
        // Fn+W = 上箭头
        // Fn+A = 左箭头
        // Fn+S = 下箭头
        // Fn+D = 右箭头
        // Fn+P = Page Up
        // Fn+N = Page Down
        // Fn+1-9 = F1-F9
        // ...
    }
}
```


### 2. TermuxTerminalSessionActivityClient

**职责**：管理终端会话的生命周期

**关键方法**：
```java
// 添加新会话
void addNewSession(boolean isFailSafe, String sessionName);

// 切换会话
void switchToSession(boolean forward);
void switchToSession(int index);

// 设置当前会话
void setCurrentSession(TerminalSession session);

// 移除会话
void removeFinishedSession(TerminalSession session);

// 重命名会话
void renameSession(TerminalSession session);

// 重置会话
void onResetTerminalSession();
```

## 输入处理流程

### 1. 软键盘输入

```
用户输入
    ↓
TerminalView.onKeyDown()
    ↓
TermuxTerminalViewClient.onKeyDown()
    ↓
检查快捷键（Ctrl+Alt+...）
    ↓
TerminalSession.write()
    ↓
写入 PTY
```

### 2. 硬件键盘输入

```
用户输入
    ↓
TerminalView.onKeyDown()
    ↓
TermuxTerminalViewClient.onKeyDown()
    ↓
检查修饰键（Ctrl, Alt, Shift, Fn）
    ↓
TermuxTerminalViewClient.onCodePoint()
    ↓
TerminalSession.writeCodePoint()
    ↓
写入 PTY
```


### 3. 额外按键输入

```
用户点击额外按键
    ↓
ExtraKeysView.onClick()
    ↓
TermuxTerminalExtraKeys.onTerminalExtraKeyButtonClick()
    ↓
TerminalSession.write() 或 writeCodePoint()
    ↓
写入 PTY
```

## 配置系统

### 1. TermuxAppSharedPreferences

**存储位置**：SharedPreferences

**配置项**：
```java
// 字体大小
int getFontSize();
void changeFontSize(boolean increase);

// 保持屏幕常亮
boolean shouldKeepScreenOn();

// 显示工具栏
boolean shouldShowTerminalToolbar();

// 软键盘
boolean isSoftKeyboardEnabled();
boolean isSoftKeyboardEnabledOnlyIfNoHardware();
```

### 2. TermuxAppSharedProperties

**存储位置**：`~/.termux/termux.properties`

**配置项**：
```properties
# 额外按键
extra-keys = [[...]]

# 全屏
fullscreen = true

# 保持屏幕常亮
keep-screen-on = true

# 终端边距
terminal-margin-horizontal = 0
terminal-margin-vertical = 0

# 终端转录行数
terminal-transcript-rows = 2000

# 光标闪烁速率
terminal-cursor-blink-rate = 0

# 快捷键
shortcut.create-session = ctrl + t
shortcut.next-session = ctrl + 2
shortcut.previous-session = ctrl + 1
shortcut.rename-session = ctrl + n

# 音量键
volume-keys = volume

# 返回键
back-key = back
```


## 通知系统

### 前台服务通知

```java
private Notification buildNotification() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
    
    // 标题和内容
    builder.setContentTitle("Termux");
    builder.setContentText(getNotificationText());
    
    // 点击打开 TermuxActivity
    Intent intent = new Intent(this, TermuxActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
    builder.setContentIntent(pendingIntent);
    
    // 操作按钮
    builder.addAction(R.drawable.ic_new_session, "New session", newSessionIntent);
    builder.addAction(R.drawable.ic_exit, "Exit", exitIntent);
    
    return builder.build();
}

private String getNotificationText() {
    int sessions = mShellManager.mTermuxSessions.size();
    int tasks = mShellManager.mTermuxTasks.size();
    
    StringBuilder text = new StringBuilder();
    if (sessions > 0) text.append(sessions).append(" session(s)");
    if (tasks > 0) {
        if (text.length() > 0) text.append(", ");
        text.append(tasks).append(" task(s)");
    }
    if (mWakeLock != null) {
        if (text.length() > 0) text.append(" - ");
        text.append("Wake lock held");
    }
    
    return text.toString();
}
```

## 主题系统

### 1. 颜色方案

**存储位置**：`~/.termux/colors.properties`

**示例**：
```properties
background=#000000
foreground=#ffffff
cursor=#ffffff

color0=#000000
color1=#cd0000
color2=#00cd00
color3=#cdcd00
color4=#0000ee
color5=#cd00cd
color6=#00cdcd
color7=#e5e5e5
color8=#7f7f7f
color9=#ff0000
color10=#00ff00
color11=#ffff00
color12=#5c5cff
color13=#ff00ff
color14=#00ffff
color15=#ffffff
```


### 2. 字体

**存储位置**：`~/.termux/font.ttf`

**加载**：
```java
File fontFile = new File(TermuxConstants.TERMUX_FONT_FILE_PATH);
if (fontFile.exists() && fontFile.isFile()) {
    Typeface typeface = Typeface.createFromFile(fontFile);
    mTerminalView.setTypeface(typeface);
}
```

## 权限处理

### 1. 存储权限

```java
// 请求存储权限（用于 termux-setup-storage）
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(
            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
            REQUEST_STORAGE_PERMISSION
        );
    }
}
```

### 2. 电池优化

```java
// 请求禁用电池优化（用于 WakeLock）
if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) {
    PermissionUtils.requestDisableBatteryOptimizations(this);
}
```

## 快捷方式

### 1. 应用快捷方式 (shortcuts.xml)

```xml
<shortcuts>
    <shortcut
        android:shortcutId="new_session"
        android:enabled="true"
        android:icon="@drawable/ic_new_session"
        android:shortcutShortLabel="@string/new_session"
        android:shortcutLongLabel="@string/new_session">
        <intent
            android:action="android.intent.action.RUN"
            android:targetPackage="com.termux"
            android:targetClass="com.termux.app.TermuxActivity" />
    </shortcut>
    
    <shortcut
        android:shortcutId="failsafe_session"
        android:enabled="true"
        android:icon="@drawable/ic_new_session"
        android:shortcutShortLabel="@string/failsafe_session"
        android:shortcutLongLabel="@string/failsafe_session">
        <intent
            android:action="android.intent.action.RUN"
            android:targetPackage="com.termux"
            android:targetClass="com.termux.app.TermuxActivity">
            <extra
                android:name="com.termux.app.failsafe_session"
                android:value="true" />
        </intent>
    </shortcut>
</shortcuts>
```


## 如何在 Jasmine-core 中使用

Jasmine-core 框架只提供了核心的 Termux 环境（Bootstrap 安装、命令执行、包管理），**不包含 UI 层**。

如果你想在自己的应用中实现类似 Termux 的终端界面，可以参考以下方案：

### 方案 1：使用 terminal-view 库（推荐）

```gradle
dependencies {
    // Termux 的终端视图库
    implementation 'com.termux:terminal-view:0.118.0'
    implementation 'com.termux:terminal-emulator:0.118.0'
    
    // Jasmine-core Termux 环境
    implementation project(':jasmine-core:termux:termux-environment')
}
```

```kotlin
class MyTerminalActivity : AppCompatActivity() {
    private lateinit var terminalView: TerminalView
    private lateinit var termux: TermuxEnvironment
    private var currentSession: TerminalSession? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 初始化 Termux 环境
        termux = TermuxEnvironment(this)
        if (!termux.isInstalled) {
            lifecycleScope.launch {
                termux.install { progress, message ->
                    // 显示进度
                }
            }
        }
        
        // 2. 创建终端视图
        terminalView = TerminalView(this, null)
        setContentView(terminalView)
        
        // 3. 创建终端会话
        createSession()
    }
    
    private fun createSession() {
        // 使用 Termux 环境中的 bash
        val shell = File(termux.paths.prefixDir, "bin/bash").absolutePath
        
        currentSession = TerminalSession(
            shell,
            termux.paths.homeDir.absolutePath,
            arrayOf(),
            buildEnvironment(),
            0,
            object : TerminalSessionClient {
                override fun onTextChanged(changedSession: TerminalSession) {
                    terminalView.onScreenUpdated()
                }
                
                override fun onTitleChanged(changedSession: TerminalSession) {
                    // 更新标题
                }
                
                override fun onSessionFinished(finishedSession: TerminalSession) {
                    // 会话结束
                }
                
                override fun onClipboardText(session: TerminalSession, text: String) {
                    // 复制到剪贴板
                }
                
                override fun onBell(session: TerminalSession) {
                    // 响铃
                }
                
                override fun onColorsChanged(session: TerminalSession) {
                    // 颜色改变
                }
            }
        )
        
        terminalView.attachSession(currentSession)
    }
    
    private fun buildEnvironment(): Array<String> {
        val prefix = termux.paths.prefixDir.absolutePath
        return arrayOf(
            "HOME=${termux.paths.homeDir.absolutePath}",
            "PREFIX=$prefix",
            "PATH=$prefix/bin:$prefix/bin/applets",
            "LD_LIBRARY_PATH=$prefix/lib",
            "TMPDIR=${termux.paths.tmpDir.absolutePath}",
            "LANG=en_US.UTF-8",
            "TERM=xterm-256color"
        )
    }
}
```


### 方案 2：简单的命令执行界面

如果不需要完整的终端模拟器，只需要执行命令并显示结果：

```kotlin
class SimpleTerminalActivity : AppCompatActivity() {
    private lateinit var termux: TermuxEnvironment
    private lateinit var outputTextView: TextView
    private lateinit var inputEditText: EditText
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_terminal)
        
        outputTextView = findViewById(R.id.output)
        inputEditText = findViewById(R.id.input)
        
        termux = TermuxEnvironment(this)
        
        findViewById<Button>(R.id.execute).setOnClickListener {
            executeCommand(inputEditText.text.toString())
        }
    }
    
    private fun executeCommand(command: String) {
        lifecycleScope.launch {
            val result = termux.executeCommand(command)
            outputTextView.append("\$ $command\n")
            outputTextView.append(result.output)
            outputTextView.append("\n")
        }
    }
}
```

### 方案 3：只使用核心功能（无 UI）

如果只需要在后台执行 Linux 命令：

```kotlin
class MyService : Service() {
    private lateinit var termux: TermuxEnvironment
    
    override fun onCreate() {
        super.onCreate()
        termux = TermuxEnvironment(this)
    }
    
    suspend fun runScript(scriptPath: String): String {
        val result = termux.executeCommand("bash $scriptPath")
        return result.output
    }
    
    suspend fun installPackage(packageName: String) {
        termux.installPackage(packageName)
    }
}
```

## 总结

Termux 官方应用的 UI 层架构：

1. **Activity + Service 架构**：UI 在 Activity，会话管理在 Service
2. **TerminalView**：独立的终端视图库，负责渲染和输入
3. **客户端接口**：通过接口解耦 View、Session 和 Activity
4. **配置系统**：SharedPreferences + termux.properties 文件
5. **额外按键**：可配置的虚拟按键行
6. **会话管理**：支持多会话，侧边栏切换
7. **前台服务**：保持进程运行，显示通知

**Jasmine-core 的定位**：
- ✅ 提供核心 Termux 环境（Bootstrap、命令执行、包管理）
- ❌ 不提供 UI 层（由使用者根据需求自行实现）
- 💡 可以参考官方实现，使用 terminal-view 库快速构建 UI

