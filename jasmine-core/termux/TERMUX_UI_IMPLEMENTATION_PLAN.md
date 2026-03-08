# Termux UI 层实现指南

## 说明

Jasmine-core 是一个框架，只提供核心的 Termux 环境管理功能（`termux-environment` 模块）。

**框架提供**：
- `TermuxEnvironment` - 核心环境管理类
- Bootstrap 安装和管理
- 命令执行接口
- 路径配置（prefixDir, homeDir, tmpDir 等）

**应用层需要自己实现**：
- 终端 UI 界面
- 会话管理
- 用户交互

## 框架提供的功能

### TermuxEnvironment

框架提供的核心类，包含：
- `paths` - 路径配置
- `isInstalled` - 检查 Bootstrap 是否已安装
- `install()` - 安装 Bootstrap
- `executeCommand()` - 执行命令
- `installPackage()` - 安装软件包
- `setupStorageSymlinks()` - 设置存储符号链接

## 实现 UI 层的关键技术点

### 1. PTY（伪终端）机制

终端会话通过 PTY（Pseudo-Terminal）与 shell 进程通信：

- **PTY 创建**：通过 JNI 调用 `createSubprocess()` 创建 PTY master/slave 对
- **Master 端**：应用持有 master 端的文件描述符，用于读写
- **Slave 端**：shell 进程使用 slave 端作为 stdin/stdout/stderr
- **双向通信**：
  - 用户输入 → TerminalView → Master → Slave → Shell 进程
  - Shell 输出 → Slave → Master → TerminalEmulator → TerminalView

### 2. I/O 线程分离

官方实现使用三个独立线程处理 I/O，避免阻塞主线程：

- **InputReader 线程**：从 PTY master 读取 shell 输出
  - 持续读取数据到缓冲区
  - 写入队列 `mProcessToTerminalIOQueue`
  - 通知主线程有新数据
  
- **OutputWriter 线程**：向 PTY master 写入用户输入
  - 从队列 `mTerminalToProcessIOQueue` 读取数据
  - 写入到 PTY master
  - 阻塞式写入，确保数据完整发送
  
- **Waiter 线程**：等待 shell 进程退出
  - 调用 `waitFor()` 等待进程结束
  - 获取退出码
  - 通知主线程会话结束

### 3. 前台服务（Foreground Service）

为了保持终端会话持续运行，必须使用前台服务：

- **Android 8.0+ 要求**：后台服务会被系统快速杀死
- **前台通知**：必须显示持久通知，告知用户服务正在运行
- **通知内容**：
  - 会话数量
  - 后台任务数量
  - WakeLock 状态
  - 操作按钮（新建会话、退出等）
- **生命周期**：只要有会话运行，服务就保持前台状态

### 4. WakeLock（唤醒锁）

防止设备休眠导致后台任务中断：

- **PowerManager.WakeLock**：保持 CPU 运行
- **WifiManager.WifiLock**：保持 Wi-Fi 连接
- **使用场景**：
  - 长时间运行的脚本
  - 网络下载任务
  - 编译构建任务
- **电池影响**：持有 WakeLock 会增加电池消耗，需要在通知中明确显示
- **用户控制**：应提供按钮让用户手动获取/释放 WakeLock

### 5. 电池优化豁免

Android 6.0+ 的 Doze 模式会限制后台应用：

- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限**：请求豁免电池优化
- **用户授权**：需要用户在系统设置中手动允许
- **必要性**：
  - 保证后台任务不被系统杀死
  - 保证 WakeLock 正常工作
  - 保证定时任务准时执行
- **最佳实践**：在用户首次获取 WakeLock 时提示申请

### 6. 主线程与工作线程

严格的线程分离确保 UI 流畅：

- **主线程（UI 线程）**：
  - 处理 TerminalView 的绘制
  - 处理用户输入事件
  - 更新 UI 状态
  - 通过 Handler 接收 I/O 线程的消息
  
- **I/O 线程**：
  - 读写 PTY 文件描述符
  - 处理数据队列
  - 等待进程退出
  
- **通信机制**：
  - I/O 线程 → 主线程：通过 Handler 发送消息
  - 主线程 → I/O 线程：通过线程安全的队列传递数据

### 7. 会话管理

Service 负责管理所有终端会话：

- **会话列表**：维护所有活动的 TerminalSession
- **当前会话**：跟踪用户当前查看的会话
- **会话切换**：支持在多个会话间切换
- **会话清理**：
  - 进程退出时自动清理
  - 用户手动关闭会话
  - Service 销毁时清理所有会话

### 8. 权限要求

实现完整功能需要以下权限：

- **FOREGROUND_SERVICE**：运行前台服务（Android 9+）
- **WAKE_LOCK**：持有唤醒锁
- **INTERNET**：apt 下载软件包
- **READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE**：访问外部存储（termux-setup-storage）
- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**：请求电池优化豁免（可选）

### 9. 性能考虑

- **避免主线程阻塞**：所有 I/O 操作在独立线程
- **缓冲区大小**：通常使用 4KB 缓冲区平衡性能和内存
- **队列容量**：限制队列大小防止内存溢出
- **绘制优化**：TerminalView 只重绘变化的区域
- **会话数量限制**：建议限制同时运行的会话数量

### 10. 终端模拟器（TerminalEmulator）

负责解析和处理终端控制序列：

- **ANSI/VT100 转义序列**：解析颜色、光标移动、清屏等控制码
- **屏幕缓冲区**：维护当前显示的字符矩阵
- **滚动缓冲区**：保存历史输出（transcript rows）
- **字符编码**：支持 UTF-8 编码
- **光标状态**：跟踪光标位置、样式（块状/下划线/竖线）
- **文本样式**：支持粗体、斜体、下划线、颜色等
- **Unicode 支持**：正确处理宽字符（CJK 字符占 2 列）

### 11. 剪贴板集成

终端与系统剪贴板的交互：

- **复制**：
  - 长按进入文本选择模式
  - 拖动选择手柄选择文本
  - 通过 ClipboardManager 复制到系统剪贴板
  - 支持 OSC 52 转义序列（程序主动复制）
  
- **粘贴**：
  - 从系统剪贴板读取文本
  - 写入到 PTY，发送给 shell
  - 需要处理特殊字符（换行符等）
  
- **文本选择模式**：
  - 显示选择手柄（左右两个）
  - 实时更新选择区域
  - 上下文菜单（复制、粘贴、分享、更多）

### 12. 响铃（Bell）处理

终端响铃事件的处理：

- **触发条件**：shell 输出 ASCII 7（BEL 字符）
- **振动反馈**：通过 Vibrator 提供触觉反馈
- **可配置**：用户可以启用/禁用振动
- **Android 版本适配**：
  - Android 8.0+：使用 VibrationEffect
  - 旧版本：使用传统 vibrate() 方法

### 13. 软键盘管理

处理软键盘的显示和隐藏：

- **自动显示**：Activity 启动时自动显示键盘
- **手动切换**：提供按钮切换键盘显示状态
- **InputMethodManager**：使用系统服务控制键盘
- **窗口模式**：`adjustResize` 确保键盘不遮挡终端内容
- **硬件键盘检测**：检测是否连接了硬件键盘

### 14. 配置系统

支持灵活的配置选项：

- **termux.properties 文件**：
  - 位置：`~/.termux/termux.properties`
  - 格式：键值对配置
  - 热加载：修改后自动重新加载
  
- **可配置项**：
  - 额外按键布局
  - 全屏模式
  - 保持屏幕常亮
  - 终端边距
  - 滚动缓冲区行数
  - 光标闪烁速率
  - 快捷键映射
  - 音量键功能
  - 返回键行为
  
- **SharedPreferences**：
  - 字体大小
  - 是否显示工具栏
  - 软键盘设置

### 15. 字体和颜色

自定义终端外观：

- **字体**：
  - 默认使用等宽字体
  - 支持自定义字体：`~/.termux/font.ttf`
  - 使用 Typeface.createFromFile() 加载
  
- **颜色方案**：
  - 默认黑底白字
  - 支持自定义：`~/.termux/colors.properties`
  - 配置项：background, foreground, cursor, color0-color15
  - 256 色支持

### 16. 屏幕方向和配置变化

处理设备旋转和配置变化：

- **configChanges 声明**：在 AndroidManifest.xml 中声明处理的配置变化
- **避免重建**：防止旋转时 Activity 重建导致会话丢失
- **动态调整**：
  - 重新计算终端行列数
  - 调整字体大小以适应屏幕
  - 更新 PTY 窗口大小（通过 ioctl）
- **多窗口模式**：支持分屏模式

### 17. 文本选择和复制模式

高级文本选择功能：

- **选择手柄**：
  - 左手柄：标记选择起点
  - 右手柄：标记选择终点
  - 自动调整方向（避免超出屏幕）
  
- **选择模式**：
  - 长按进入选择模式
  - 拖动手柄调整选择范围
  - 双击选择单词
  - 三击选择整行
  
- **ActionMode**：
  - 显示浮动工具栏
  - 提供复制、粘贴、分享等操作
  - 自定义菜单项

### 18. 额外按键（Extra Keys）

提供常用的特殊按键：

- **默认布局**：ESC, TAB, CTRL, ALT, 方向键等
- **可配置**：通过 termux.properties 自定义布局
- **特殊按键**：
  - 修饰键：CTRL, ALT, SHIFT, FN（可切换状态）
  - 功能键：F1-F12
  - 导航键：HOME, END, PGUP, PGDN
  - 常用符号：/, -, |, ~
- **组合键支持**：CTRL+C, CTRL+Z 等

### 19. 会话持久化

保持会话在后台运行：

- **Service 生命周期**：独立于 Activity
- **进程保活**：
  - 前台服务防止被杀
  - WakeLock 防止休眠
  - 电池优化豁免
- **会话恢复**：
  - Activity 重建后重新绑定 Service
  - 恢复当前会话状态
  - 保持终端输出历史

### 20. 通知管理

前台服务通知的详细信息：

- **通知渠道**（Android 8.0+）：
  - 创建专用通知渠道
  - 设置重要性级别（LOW）
  
- **通知内容**：
  - 标题：应用名称
  - 文本：会话数量、任务数量
  - WakeLock 状态提示
  
- **通知操作**：
  - 点击：打开 Activity
  - 操作按钮：新建会话、切换 WakeLock、退出
  
- **持久通知**：设置为 ongoing，用户无法滑动关闭

## 参考资料

如果你需要在应用中实现 Termux UI，可以参考：

1. **Termux 官方源码**：`termux-app-master/` 目录
2. **Termux 源码分析文档**：`termux-app-master/Termux源码分析文档/`
3. **UI 架构文档**：`jasmine-core/TERMUX_UI_ARCHITECTURE.md`
4. **Terminal 库**：
   - `terminal-view` - 终端视图组件
   - `terminal-emulator` - 终端模拟器

## 总结

Jasmine-core 框架专注于提供核心的 Termux 环境功能，不包含 UI 层。这种设计确保了：
- 框架保持轻量和专注
- 应用层有完全的 UI 自定义能力
- 不同应用可以根据自己的需求实现不同的 UI
