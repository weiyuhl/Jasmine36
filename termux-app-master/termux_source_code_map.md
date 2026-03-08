# Termux 源码路径完整导航

## 📁 项目根目录结构

```
termux-app/
├── app/                              # 主应用模块
├── terminal-emulator/                # 终端模拟器库
├── terminal-view/                    # 终端视图库
├── termux-shared/                    # 共享库
├── build.gradle                      # 根构建配置
├── settings.gradle                   # 模块配置
└── README.md                         # 项目说明
```

---

## 🎯 核心功能模块路径

### 1. 应用入口和生命周期

#### 应用类
```
app/src/main/java/com/termux/app/TermuxApplication.java
```
- 应用启动入口
- 初始化崩溃处理、日志、Shell 管理器
- 设置夜间模式
- 初始化 termux-am-socket 服务器

#### 主 Activity
```
app/src/main/java/com/termux/app/TermuxActivity.java
```
- 主界面 Activity
- 管理终端视图、会话列表、工具栏
- 处理用户交互（菜单、按键、触摸）
- 绑定到 TermuxService

#### 设置 Activity
```
app/src/main/java/com/termux/app/activities/SettingsActivity.java
app/src/main/java/com/termux/app/activities/HelpActivity.java
```

---

### 2. 服务层

#### 主服务
```
app/src/main/java/com/termux/app/TermuxService.java
```
- 前台服务，管理所有会话和任务
- 维护 mTermuxSessions（前台会话）和 mTermuxTasks（后台任务）
- 处理 WakeLock 和 WifiLock
- 处理 ACTION_SERVICE_EXECUTE 等 Intent

#### 命令执行服务
```
app/src/main/java/com/termux/app/RunCommandService.java
```
- 接收来自插件的 RUN_COMMAND Intent
- 验证权限和参数
- 转发到 TermuxService 执行

---

### 3. 终端模拟器核心

#### 终端会话
```
terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java
```
- 管理单个终端会话
- 创建 PTY 和子进程
- 启动 I/O 线程（InputReader、OutputWriter、Waiter）
- 管理进程生命周期

#### 终端模拟器
```
terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java
```
- VT100/xterm 协议解析（2600+ 行）
- 状态机处理转义序列
- 管理屏幕缓冲区、光标、颜色
- 支持鼠标跟踪、备用屏幕等高级特性

#### 终端缓冲区
```
terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java
terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java
```
- 管理终端屏幕数据
- 处理滚动、历史记录

#### JNI 接口
```
terminal-emulator/src/main/java/com/termux/terminal/JNI.java
```
- Java 到 Native 的桥接
- 声明 native 方法

---

### 4. Native 代码（C/JNI）

#### PTY 创建和管理
```
terminal-emulator/src/main/jni/termux.c
```
- `createSubprocess()` - 创建 PTY 和 fork 进程
- `setPtyWindowSize()` - 调整窗口大小
- `setPtyUTF8Mode()` - 设置 UTF-8 模式
- `waitFor()` - 等待进程退出
- `close()` - 关闭文件描述符

#### Bootstrap 嵌入
```
app/src/main/cpp/termux-bootstrap.c
app/src/main/cpp/termux-bootstrap-zip.S
app/src/main/cpp/Android.mk
```
- `termux-bootstrap.c` - 提供 getZip() JNI 方法
- `termux-bootstrap-zip.S` - 汇编代码，使用 .incbin 嵌入 ZIP
- `Android.mk` - Native 构建配置

---

### 5. 终端视图

#### 主视图
```
terminal-view/src/main/java/com/termux/view/TerminalView.java
```
- 自定义 View，渲染终端
- 处理触摸输入、滚动、文本选择
- 光标闪烁动画
- AutoFill 支持

#### 渲染器
```
terminal-view/src/main/java/com/termux/view/TerminalRenderer.java
```
- 绘制文本、光标、选择区域
- 处理字体、颜色

#### 文本选择
```
terminal-view/src/main/java/com/termux/view/textselection/TextSelectionCursorController.java
terminal-view/src/main/java/com/termux/view/textselection/TextSelectionHandleView.java
```

---

### 6. 会话管理

#### Termux 会话包装
```
termux-shared/src/main/java/com/termux/shared/termux/shell/command/runner/terminal/TermuxSession.java
```
- 包装 TerminalSession
- 关联 ExecutionCommand
- 处理会话退出和结果

#### 后台任务
```
termux-shared/src/main/java/com/termux/shared/shell/command/runner/app/AppShell.java
```
- 管理后台任务执行
- 使用 Runtime.exec() 而非 PTY
- 处理标准输入/输出/错误

#### Shell 管理器
```
termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellManager.java
```
- 单例管理所有会话和任务
- 维护会话列表、任务列表、待处理命令列表
- 分配 Shell ID

---

### 7. 客户端接口实现

#### Activity 客户端
```
app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java
```
- 需要 Activity 引用的会话客户端
- 处理字体、颜色、铃声
- 管理会话切换

#### Service 客户端
```
app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionServiceClient.java
```
- 不需要 Activity 引用的会话客户端
- 服务层的基础实现

#### View 客户端
```
app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java
```
- 处理键盘输入、特殊键
- URL 选择、文本分享
- 软键盘管理

---

### 8. Bootstrap 安装

#### 安装器
```
app/src/main/java/com/termux/app/TermuxInstaller.java
```
- `setupBootstrapIfNeeded()` - 检查并安装 bootstrap
- `loadZipBytes()` - 从 native 代码获取 ZIP
- 解压 ZIP 到应用目录
- 创建符号链接
- 设置存储链接

---

### 9. 环境配置

#### Shell 环境
```
termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java
```
- 设置环境变量（HOME, PREFIX, PATH, TMPDIR）
- 写入环境文件到 ~/.termux/termux.env

#### Bootstrap 配置
```
termux-shared/src/main/java/com/termux/shared/termux/TermuxBootstrap.java
```
- 定义包管理器（APT）
- 定义包变体（apt-android-7, apt-android-5）
- 管理 bootstrap 版本

---

### 10. 常量定义

#### 核心常量
```
termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java
```
- 路径常量（PREFIX, HOME, TMP 等）
- Intent Action 和 Extra
- 通知 ID 和频道
- URL 和包名

---

### 11. UI 组件

#### 根视图
```
app/src/main/java/com/termux/app/terminal/TermuxActivityRootView.java
```
- Activity 的根视图
- 处理软键盘显示

#### 会话列表
```
app/src/main/java/com/termux/app/terminal/TermuxSessionsListViewController.java
```
- 管理左侧抽屉的会话列表
- 处理会话切换、重命名

#### 额外按键
```
app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java
termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java
```
- 屏幕底部的额外按键工具栏
- 特殊按钮（ESC, CTRL, ALT, TAB）

#### 工具栏
```
app/src/main/java/com/termux/app/terminal/io/TerminalToolbarViewPager.java
```
- 底部工具栏的 ViewPager

---

### 12. 文件和权限

#### 文件工具
```
termux-shared/src/main/java/com/termux/shared/termux/file/TermuxFileUtils.java
termux-shared/src/main/java/com/termux/shared/file/FileUtils.java
```
- 文件路径验证
- 权限检查和设置
- 目录创建

---

### 13. 插件通信

#### 插件工具
```
termux-shared/src/main/java/com/termux/shared/termux/plugins/TermuxPluginUtils.java
```
- 验证 allow-external-apps 策略
- 处理插件执行结果

#### AM Socket 服务器
```
termux-shared/src/main/java/com/termux/shared/termux/shell/am/TermuxAmSocketServer.java
```
- 本地 Socket 服务器
- 处理插件的 am 命令

---

### 14. 配置和属性

#### 应用属性
```
termux-shared/src/main/java/com/termux/shared/termux/settings/properties/TermuxAppSharedProperties.java
termux-shared/src/main/java/com/termux/shared/termux/settings/properties/TermuxPropertyConstants.java
```
- 从 termux.properties 加载配置
- 定义属性键和默认值

#### 应用偏好设置
```
termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxAppSharedPreferences.java
```
- SharedPreferences 管理
- 日志级别、字体大小等设置

---

### 15. 执行命令模型

#### 执行命令
```
termux-shared/src/main/java/com/termux/shared/shell/command/ExecutionCommand.java
```
- 封装命令执行参数
- 状态管理（EXECUTING, EXECUTED, FAILED）
- 结果数据（stdout, stderr, exitCode）

---

### 16. 工具类

#### 日志
```
termux-shared/src/main/java/com/termux/shared/logger/Logger.java
```
- 统一日志接口
- 支持不同日志级别

#### Shell 工具
```
termux-shared/src/main/java/com/termux/shared/shell/ShellUtils.java
termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellUtils.java
```
- Shell 命令处理
- 参数解析

#### 数据工具
```
termux-shared/src/main/java/com/termux/shared/data/DataUtils.java
termux-shared/src/main/java/com/termux/shared/data/IntentUtils.java
```

---

### 17. 布局文件

#### 主布局
```
app/src/main/res/layout/activity_termux.xml
```
- TermuxActivity 的布局
- 包含 TerminalView、抽屉、工具栏

#### 其他布局
```
app/src/main/res/layout/drawer_sessions_list.xml
app/src/main/res/layout/view_terminal_toolbar.xml
app/src/main/res/layout/view_terminal_toolbar_extra_keys.xml
```

---

### 18. 资源文件

#### 字符串
```
app/src/main/res/values/strings.xml
termux-shared/src/main/res/values/strings.xml
```

#### 样式和主题
```
app/src/main/res/values/styles.xml
app/src/main/res/values/colors.xml
```

#### 配置
```
app/src/main/res/xml/shortcuts.xml
```

---

### 19. Gradle 构建配置

#### 根配置
```
build.gradle                          # 根项目配置
settings.gradle                       # 模块配置
gradle.properties                     # Gradle 属性
```

#### 应用配置
```
app/build.gradle                      # 应用模块配置
  - downloadBootstraps() 任务         # 下载 bootstrap
  - 签名配置
  - 构建类型
  - APK 命名
```

#### 库配置
```
terminal-emulator/build.gradle
terminal-view/build.gradle
termux-shared/build.gradle
```

---

### 20. 清单文件

```
app/src/main/AndroidManifest.xml
```
- 应用权限声明
- Activity、Service、Receiver、Provider 声明
- Intent Filter 配置

---

## 🎯 关键流程的代码路径

### 流程 1：应用启动

```
1. app/src/main/java/com/termux/app/TermuxApplication.java
   → onCreate()

2. app/src/main/java/com/termux/app/TermuxActivity.java
   → onCreate()
   → bindService()

3. app/src/main/java/com/termux/app/TermuxService.java
   → onCreate()
   → onBind()

4. app/src/main/java/com/termux/app/TermuxInstaller.java
   → setupBootstrapIfNeeded()
```

### 流程 2：创建终端会话

```
1. app/src/main/java/com/termux/app/TermuxService.java
   → actionServiceExecute()

2. termux-shared/src/main/java/com/termux/shared/termux/shell/command/runner/terminal/TermuxSession.java
   → execute()

3. terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java
   → new TerminalSession()
   → initializeEmulator()

4. terminal-emulator/src/main/jni/termux.c
   → createSubprocess()
```

### 流程 3：处理用户输入

```
1. terminal-view/src/main/java/com/termux/view/TerminalView.java
   → onKeyDown()

2. app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java
   → onKeyDown()

3. terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java
   → writeCodePoint()
   → write()
```

### 流程 4：处理进程输出

```
1. terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java
   → InputReader 线程
   → mProcessToTerminalIOQueue.write()
   → MSG_NEW_INPUT

2. terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java
   → process()

3. terminal-view/src/main/java/com/termux/view/TerminalView.java
   → invalidate()
   → onDraw()
```

### 流程 5：插件命令执行

```
1. app/src/main/java/com/termux/app/RunCommandService.java
   → onStartCommand()

2. app/src/main/java/com/termux/app/TermuxService.java
   → actionServiceExecute()

3. termux-shared/src/main/java/com/termux/shared/shell/command/runner/app/AppShell.java
   → execute()
```

---

## 📚 学习路径建议

### 初学者路径
```
1. TermuxConstants.java          # 了解常量定义
2. TermuxApplication.java        # 应用入口
3. TermuxActivity.java           # UI 入口
4. TerminalSession.java          # 会话管理
5. termux.c                      # PTY 创建
```

### 进阶路径
```
1. TerminalEmulator.java         # 协议解析
2. TerminalView.java             # 视图渲染
3. TermuxService.java            # 服务管理
4. TermuxSession.java            # 会话包装
5. TermuxInstaller.java          # Bootstrap 安装
```

### 高级路径
```
1. 完整的 I/O 流程
2. 插件通信机制
3. 环境变量设置
4. 构建系统
5. Native 代码优化
```

---

## 🔍 快速查找

### 想了解某个功能？

- **PTY 创建** → `terminal-emulator/src/main/jni/termux.c`
- **协议解析** → `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java`
- **视图渲染** → `terminal-view/src/main/java/com/termux/view/TerminalView.java`
- **会话管理** → `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java`
- **服务管理** → `app/src/main/java/com/termux/app/TermuxService.java`
- **Bootstrap** → `app/src/main/java/com/termux/app/TermuxInstaller.java`
- **环境变量** → `termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java`
- **插件通信** → `app/src/main/java/com/termux/app/RunCommandService.java`

---

## 📝 文件命名规范

```
Activity:     *Activity.java
Service:      *Service.java
Fragment:     *Fragment.java
View:         *View.java
Client:       *Client.java
Utils:        *Utils.java
Constants:    *Constants.java
Manager:      *Manager.java
```

这个导航应该能帮助你快速定位到任何你想了解的功能！
