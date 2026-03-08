# Termux 工作原理实例

## 🔍 实际例子：安装和运行 Python

### 步骤 1：首次启动 Termux

```
用户打开 Termux 应用
    ↓
TermuxActivity.onCreate()
    ↓
检查 /data/data/com.termux/files/usr/ 是否存在
    ↓
不存在！需要安装 Bootstrap
    ↓
TermuxInstaller.setupBootstrapIfNeeded()
```

### 步骤 2：安装 Bootstrap

```java
// 1. 从 APK 内嵌的 native 代码获取 ZIP
byte[] bootstrapZip = TermuxInstaller.getZip();
// 这个 ZIP 文件在编译时被嵌入到 APK 中

// 2. 解压到临时目录
/data/data/com.termux/files/usr-staging/
├── bin/
│   ├── bash          (ARM64 可执行文件)
│   ├── ls
│   ├── cat
│   └── apt
├── lib/
│   ├── libc.so       (Android Bionic libc)
│   └── libssl.so
└── etc/
    └── apt/sources.list

// 3. 设置权限
chmod 0700 bin/*

// 4. 创建符号链接
ln -s bash bin/sh

// 5. 移动到最终位置
mv usr-staging usr
```

### 步骤 3：启动 Shell

```java
// TermuxSession.execute()

// 设置环境变量
String[] env = {
    "HOME=/data/data/com.termux/files/home",
    "PREFIX=/data/data/com.termux/files/usr",
    "PATH=/data/data/com.termux/files/usr/bin:/system/bin",
    "SHELL=/data/data/com.termux/files/usr/bin/bash",
    "TERM=xterm-256color"
};

// 通过 JNI 创建 PTY 和进程
int ptm = JNI.createSubprocess(
    "/data/data/com.termux/files/usr/bin/bash",  // shell 路径
    "/data/data/com.termux/files/home",          // 工作目录
    new String[]{"-bash"},                       // 参数（登录 shell）
    env,                                         // 环境变量
    processId,
    rows, cols
);
```

### 步骤 4：用户安装 Python

```bash
$ apt update
```

**实际发生的事情：**

```
1. 用户输入 "apt update"
   ↓
2. Bash 在 PATH 中查找 "apt"
   → 找到 /data/data/com.termux/files/usr/bin/apt
   ↓
3. Fork 新进程执行 apt
   ↓
4. apt 读取配置
   → /data/data/com.termux/files/usr/etc/apt/sources.list
   ↓
5. apt 连接到 https://packages.termux.dev/
   ↓
6. 下载包列表
   → 保存到 /data/data/com.termux/files/usr/var/lib/apt/
   ↓
7. 输出 "Reading package lists... Done"
```

```bash
$ apt install python
```

**实际发生的事情：**

```
1. apt 查询包数据库
   → python 包在 packages.termux.dev
   ↓
2. 下载 python_3.11.deb (约 15MB)
   → 保存到 /data/data/com.termux/files/usr/var/cache/apt/
   ↓
3. 解压 .deb 包
   python_3.11.deb 内容：
   ├── usr/bin/python3.11        (ARM64 ELF 可执行文件)
   ├── usr/lib/libpython3.11.so  (共享库)
   ├── usr/lib/python3.11/       (Python 标准库)
   └── usr/share/doc/python/     (文档)
   ↓
4. 安装到 PREFIX
   → 复制文件到 /data/data/com.termux/files/usr/
   ↓
5. 创建符号链接
   → ln -s python3.11 /data/data/com.termux/files/usr/bin/python
   ↓
6. 运行安装后脚本
   → 编译 Python 字节码 (.pyc)
```

### 步骤 5：运行 Python 程序

```bash
$ cat > hello.py << EOF
print("Hello from Termux!")
import sys
print(f"Python version: {sys.version}")
print(f"Executable: {sys.executable}")
EOF

$ python hello.py
```

**完整执行流程：**

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 用户输入 "python hello.py"                                │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. TerminalView 捕获按键                                     │
│    - 将 "python hello.py\n" 转换为字节                       │
│    - 写入 mTerminalToProcessIOQueue                          │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. OutputWriter 线程                                         │
│    - 从队列读取数据                                           │
│    - write(ptm_fd, "python hello.py\n")                     │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. PTY 内核驱动                                              │
│    - 将数据传递到 slave 端                                    │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Bash 进程                                                 │
│    - read(stdin, buffer)                                    │
│    - 读取到 "python hello.py\n"                              │
│    - 解析命令行                                               │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. Bash 查找 "python"                                        │
│    - 在 PATH 中搜索                                           │
│    - 找到 /data/data/com.termux/files/usr/bin/python        │
│    - 这是一个符号链接 → python3.11                            │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. Bash fork() 新进程                                        │
│    pid = fork()                                             │
│    if (pid == 0) {                                          │
│        execve("/data/.../usr/bin/python3.11",              │
│               ["python", "hello.py"],                       │
│               environ)                                      │
│    }                                                        │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 8. Linux 内核加载 Python                                     │
│    - 读取 ELF 文件头                                          │
│    - 加载程序段到内存                                          │
│    - 查找动态链接器                                            │
│    - 加载共享库：                                              │
│      * libpython3.11.so                                     │
│      * libc.so                                              │
│      * libm.so                                              │
│    - 跳转到程序入口点                                          │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 9. Python 解释器启动                                          │
│    - 初始化 Python 运行时                                     │
│    - 设置 sys.path                                           │
│    - 导入内置模块                                             │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 10. Python 打开 hello.py                                     │
│     fd = open("/data/data/com.termux/files/home/hello.py") │
│     - 使用标准 Linux open() 系统调用                          │
│     - Android 内核处理文件访问                                │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 11. Python 解析和执行代码                                     │
│     - 词法分析                                                │
│     - 语法分析                                                │
│     - 编译为字节码                                             │
│     - 执行字节码                                               │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 12. print() 输出                                             │
│     write(stdout, "Hello from Termux!\n")                   │
│     - stdout 是 PTY slave 端                                 │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 13. PTY 内核驱动                                             │
│     - 将数据传递到 master 端                                  │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 14. InputReader 线程                                         │
│     - read(ptm_fd, buffer)                                  │
│     - 读取到 "Hello from Termux!\n"                          │
│     - 写入 mProcessToTerminalIOQueue                         │
│     - 发送 MSG_NEW_INPUT 消息                                │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 15. MainThreadHandler                                        │
│     - 处理 MSG_NEW_INPUT                                     │
│     - 从队列读取数据                                           │
│     - 调用 TerminalEmulator.process()                        │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 16. TerminalEmulator 处理输出                                │
│     for (byte b : "Hello from Termux!\n") {                 │
│         if (b == '\n') {                                    │
│             cursorRow++;                                    │
│             cursorCol = 0;                                  │
│         } else {                                            │
│             screen[cursorRow][cursorCol] = (char)b;         │
│             cursorCol++;                                    │
│         }                                                   │
│     }                                                       │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 17. TerminalView.invalidate()                               │
│     - 标记视图需要重绘                                         │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 18. TerminalView.onDraw()                                   │
│     - 遍历屏幕缓冲区                                           │
│     - 绘制每个字符到 Canvas                                    │
│     - 用户看到输出！                                           │
└─────────────────────────────────────────────────────────────┘
```

## 🔬 深入细节：Python 如何找到库

```bash
$ python -c "import sys; print(sys.path)"
```

**输出：**
```python
[
    '',
    '/data/data/com.termux/files/usr/lib/python3.11',
    '/data/data/com.termux/files/usr/lib/python3.11/lib-dynload',
    '/data/data/com.termux/files/usr/lib/python3.11/site-packages'
]
```

**为什么是这些路径？**

```c
// Python 编译时的配置
./configure \
    --prefix=/data/data/com.termux/files/usr \
    --host=aarch64-linux-android

// 生成的 pyconfig.h
#define PREFIX "/data/data/com.termux/files/usr"
#define PYTHONPATH PREFIX "/lib/python3.11"
```

## 🎯 关键理解

### 1. 没有虚拟化

```
传统虚拟机：
Android → QEMU → Linux 内核 → Linux 用户空间
         (慢！)

Termux：
Android 的 Linux 内核 → Linux 用户空间
                      (快！)
```

### 2. 所有程序都是原生的

```bash
$ file /data/data/com.termux/files/usr/bin/python
python: ELF 64-bit LSB executable, ARM aarch64, version 1 (SYSV), 
        dynamically linked, interpreter /system/bin/linker64

# 这是真正的 ARM64 Linux 可执行文件！
# 不是脚本，不是 Java，不是模拟
```

### 3. 使用 Android 的 Linux 内核

```bash
$ uname -a
Linux localhost 5.10.43-android12-9-00001-g7c8a39f4b2b8 #1 SMP PREEMPT 
aarch64 Android

# 这是 Android 的内核
# 但它就是 Linux 内核！
```

### 4. 在应用沙箱内

```bash
$ id
uid=10123(u0_a123) gid=10123(u0_a123) groups=...

$ pwd
/data/data/com.termux/files/home

$ ls /
ls: cannot open directory '/': Permission denied

# 只能访问自己的目录
# 这是 Android 的安全机制
```

## 📊 性能对比

```
运行 Python 计算斐波那契数列（n=35）：

原生 Linux (笔记本):     0.8 秒
Termux (手机):           1.2 秒  ← 几乎原生性能！
UserLAnd (PRoot):        3.5 秒  ← 有虚拟化开销
Linux Deploy (Chroot):   1.3 秒  ← 需要 root
```

## 🎓 总结

Termux 的"魔法"其实很简单：

1. **Android 本身就是 Linux**
   - 不需要虚拟化
   - 直接使用 Linux 内核

2. **提供 Linux 工具**
   - Bootstrap 包含基础工具
   - 交叉编译到 ARM
   - 使用 Termux 的路径

3. **设置正确的环境**
   - 环境变量
   - 文件权限
   - 符号链接

4. **在沙箱内运行**
   - 安全
   - 不需要 root
   - 易于安装/卸载

**核心理念：**
```
不是"在 Android 上模拟 Linux"
而是"在 Android 的 Linux 上运行 Linux 程序"
```

就像你在 Windows 上运行 WSL：
- Windows 提供 Linux 内核
- WSL 提供 Linux 用户空间
- 程序原生运行

Termux 也是一样：
- Android 提供 Linux 内核
- Termux 提供 Linux 用户空间
- 程序原生运行
