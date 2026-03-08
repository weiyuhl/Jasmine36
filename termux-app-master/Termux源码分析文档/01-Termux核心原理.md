# Termux 核心原理解析

## 核心问题：为什么不能直接用 Process？

### 方案 A：使用普通 Process (❌ 不行)

```java
// 这样做有什么问题？
Process process = Runtime.getRuntime().exec("/system/bin/sh");
InputStream stdout = process.getInputStream();
OutputStream stdin = process.getOutputStream();
```

**问题：**
1. **没有终端环境** - Shell 检测到不是 TTY，行为会改变
2. **无法处理 Ctrl+C** - 无法发送信号给进程
3. **无法调整窗口大小** - 程序不知道终端有多大
4. **交互式程序失败** - vim, nano, top 等程序无法运行
5. **没有作业控制** - 无法前台/后台切换

### 方案 B：使用 PTY (✅ 正确)

```c
// 创建伪终端
int master_fd = open("/dev/ptmx", O_RDWR);
grantpt(master_fd);
unlockpt(master_fd);
char *slave_name = ptsname(master_fd);

// Fork 子进程
pid_t pid = fork();
if (pid == 0) {
    // 子进程：打开 slave 端
    int slave_fd = open(slave_name, O_RDWR);
    
    // 创建新会话，设置为控制终端
    setsid();
    ioctl(slave_fd, TIOCSCTTY, 0);
    
    // 重定向标准流
    dup2(slave_fd, 0);  // stdin
    dup2(slave_fd, 1);  // stdout
    dup2(slave_fd, 2);  // stderr
    
    // 执行 shell
    execl("/system/bin/sh", "sh", NULL);
}

// 父进程：通过 master_fd 与 shell 通信
```

**优势：**
- ✅ Shell 认为自己在真实终端中运行
- ✅ 支持所有终端控制字符
- ✅ 支持窗口大小调整 (TIOCSWINSZ)
- ✅ 支持信号传递 (Ctrl+C → SIGINT)
- ✅ 交互式程序正常工作

## 核心 2: 终端协议解析

### 什么是终端协议？

当你在终端中运行程序时，程序不是直接画像素，而是发送**控制序列**：

```bash
# 程序输出的不是这样：
"Hello World"

# 而是这样：
"\033[31mHello\033[0m \033[1mWorld\033[0m"
#  ^^^^^ 红色   ^^^^^ 重置  ^^^^^ 粗体  ^^^^^ 重置
```

### 常见的转义序列

```
清屏：         \033[2J
光标移动：     \033[10;5H      (移动到第10行第5列)
设置颜色：     \033[31m        (红色)
粗体：         \033[1m
重置：         \033[0m
删除行：       \033[2K
滚动：         \033[1S
```

### Termux 如何解析？

```java
// 简化版的状态机
public class TerminalEmulator {
    private int state = STATE_NORMAL;
    private StringBuilder escapeBuffer = new StringBuilder();
    
    public void process(byte b) {
        switch (state) {
            case STATE_NORMAL:
                if (b == 27) {  // ESC 字符
                    state = STATE_ESCAPE;
                } else if (b == '\n') {
                    cursorRow++;
                    cursorCol = 0;
                } else {
                    // 普通字符，直接显示
                    screen[cursorRow][cursorCol] = (char)b;
                    cursorCol++;
                }
                break;
                
            case STATE_ESCAPE:
                if (b == '[') {
                    state = STATE_CSI;  // Control Sequence Introducer
                    escapeBuffer.setLength(0);
                } else if (b == ']') {
                    state = STATE_OSC;  // Operating System Command
                }
                break;
                
            case STATE_CSI:
                if (b >= '0' && b <= '9' || b == ';') {
                    escapeBuffer.append((char)b);
                } else {
                    // 终结字符，执行命令
                    executeCSI(escapeBuffer.toString(), b);
                    state = STATE_NORMAL;
                }
                break;
        }
    }
    
    private void executeCSI(String params, byte command) {
        switch (command) {
            case 'H':  // 光标移动
                String[] parts = params.split(";");
                cursorRow = Integer.parseInt(parts[0]) - 1;
                cursorCol = Integer.parseInt(parts[1]) - 1;
                break;
                
            case 'J':  // 清屏
                if (params.equals("2")) {
                    clearScreen();
                }
                break;
                
            case 'm':  // 设置图形属性
                setGraphicsMode(params);
                break;
        }
    }
}
```

## 核心流程图

```
┌─────────────────────────────────────────────────────────┐
│                    完整数据流                             │
└─────────────────────────────────────────────────────────┘

用户按键 "ls"
    ↓
TerminalView.onKeyDown()
    ↓
将 "ls" 转换为字节
    ↓
write(master_fd, "ls\n")  ← 写入 PTY master 端
    ↓
PTY 内核驱动
    ↓
Shell 从 slave 端读取 "ls\n"
    ↓
Shell 执行 ls 命令
    ↓
ls 输出: "\033[0mfile1.txt\nfile2.txt\n"
    ↓
Shell 写入到 slave 端
    ↓
PTY 内核驱动
    ↓
read(master_fd) ← 从 PTY master 端读取
    ↓
TerminalEmulator.process()
    ↓
解析 "\033[0m" → 重置颜色
解析 "file1.txt\n" → 显示文本并换行
解析 "file2.txt\n" → 显示文本并换行
    ↓
更新屏幕缓冲区
    ↓
TerminalView.invalidate()
    ↓
TerminalView.onDraw()
    ↓
用户看到输出
```

## 最小可行实现

这是一个**最简化**的终端模拟器核心：

```java
// 只需要 200 行代码就能实现基本功能！

public class MinimalTerminal {
    private int masterFd;
    private char[][] screen = new char[24][80];
    private int cursorRow = 0, cursorCol = 0;
    
    // 1. 创建 PTY 和进程
    public void start() {
        masterFd = createPtyProcess("/system/bin/sh");
        
        // 启动读取线程
        new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (true) {
                int n = read(masterFd, buffer);
                processOutput(buffer, n);
                updateDisplay();
            }
        }).start();
    }
    
    // 2. 处理输出（简化版）
    private void processOutput(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            byte b = data[i];
            
            if (b == '\n') {
                cursorRow++;
                cursorCol = 0;
            } else if (b == '\r') {
                cursorCol = 0;
            } else if (b >= 32 && b < 127) {
                // 可打印字符
                screen[cursorRow][cursorCol] = (char)b;
                cursorCol++;
            }
            // 忽略转义序列（简化版）
        }
    }
    
    // 3. 处理输入
    public void sendInput(String text) {
        write(masterFd, text.getBytes());
    }
    
    // 4. 显示
    private void updateDisplay() {
        for (int row = 0; row < 24; row++) {
            System.out.println(new String(screen[row]));
        }
    }
    
    // Native 方法
    private native int createPtyProcess(String shell);
    private native int read(int fd, byte[] buffer);
    private native void write(int fd, byte[] data);
}
```

## 为什么 Termux 有 2600+ 行？

因为要支持**完整的终端功能**：

1. **完整的 VT100/xterm 协议**
   - 数百种转义序列
   - 光标控制（上下左右、保存/恢复）
   - 屏幕操作（清屏、滚动、插入/删除行）
   - 颜色支持（16色、256色、真彩色）
   - 文本属性（粗体、斜体、下划线、闪烁）

2. **高级特性**
   - 鼠标跟踪
   - 备用屏幕缓冲区
   - 括号粘贴模式
   - 窗口标题设置
   - 超链接支持

3. **性能优化**
   - 脏区域标记
   - 批量更新
   - 缓冲管理

4. **边界情况处理**
   - Unicode 支持
   - 双宽字符（中文）
   - 组合字符
   - 右到左文本

## 核心代码位置

在 Termux 源码中，核心就是这几个文件：

```
terminal-emulator/src/main/jni/termux.c
    ↓
    核心函数：create_subprocess()
    作用：创建 PTY 和 fork 进程
    代码量：~150 行

terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java
    ↓
    核心函数：initializeEmulator()
    作用：启动 I/O 线程，连接 PTY
    代码量：~100 行

terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java
    ↓
    核心函数：process(byte[] data)
    作用：解析终端协议
    代码量：~2600 行（因为要支持完整协议）
```

## 总结：核心就是这么简单

```
核心 = PTY + 协议解析

PTY：让程序以为在真实终端中运行
协议解析：理解程序发送的控制序列

其他都是锦上添花：
- Service 保活
- UI 美化
- 插件系统
- 包管理
- 权限管理
```

**最小可行版本只需要：**
1. 100 行 C 代码（创建 PTY）
2. 200 行 Java 代码（基本协议解析）
3. 100 行 Java 代码（UI 显示）

总共 400 行代码就能实现一个基本的终端模拟器！

Termux 的 2 万多行代码是为了：
- 支持完整的终端协议
- 处理各种边界情况
- 提供良好的用户体验
- 支持插件和扩展
- 保证稳定性和性能
