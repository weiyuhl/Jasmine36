# Termux 如何在 Android 上创建 Linux 环境

## 🎭 核心秘密：Android 本身就是 Linux！

这是最关键的认知：

```
Android 系统架构：
┌─────────────────────────────────┐
│   Android 应用层 (Java/Kotlin)   │
├─────────────────────────────────┤
│   Android Framework (ART/JVM)   │
├─────────────────────────────────┤
│   Android Native 层 (C/C++)     │
├─────────────────────────────────┤
│   Linux 内核 (Kernel)            │  ← Android 就是 Linux！
└─────────────────────────────────┘
```

**关键事实：**
- Android 的内核就是 Linux 内核
- Android 支持所有 Linux 系统调用
- Android 有完整的 Linux 文件系统
- Android 可以运行 Linux 二进制程序

## 🔑 Termux 的"魔法"：不是虚拟化，是原生运行！

Termux **不是**：
- ❌ 虚拟机 (不像 VirtualBox)
- ❌ 容器 (不像 Docker)
- ❌ 模拟器 (不像 QEMU)
- ❌ Chroot 环境

Termux **是**：
- ✅ 在 Android 的 Linux 内核上直接运行 Linux 程序
- ✅ 使用 Android 应用的私有目录存储文件
- ✅ 通过 Bootstrap 提供 Linux 用户空间工具

## 📦 Bootstrap：Linux 用户空间的核心

### 什么是 Bootstrap？

Bootstrap 是一个包含基础 Linux 工具的压缩包：

```
bootstrap-aarch64.zip (约 30MB)
├── bin/              # 可执行文件
│   ├── bash          # Shell
│   ├── ls            # 列出文件
│   ├── cat           # 查看文件
│   ├── grep          # 搜索
│   ├── apt           # 包管理器
│   └── ...           # 数百个工具
├── lib/              # 共享库
│   ├── libc.so       # C 标准库
│   ├── libssl.so     # SSL 库
│   └── ...
├── etc/              # 配置文件
│   ├── bash.bashrc
│   └── apt/sources.list
├── usr/              # 用户程序
├── var/              # 变量数据
└── SYMLINKS.txt      # 符号链接列表
```

### Bootstrap 安装过程

```java
// 1. 从 APK 中提取 Bootstrap ZIP
byte[] zipBytes = loadZipBytes();  // 从 native 代码获取

// 2. 解压到临时目录
TERMUX_STAGING_PREFIX_DIR = /data/data/com.termux/files/usr-staging/

// 3. 解压所有文件
for (ZipEntry entry : zip) {
    if (entry.name == "SYMLINKS.txt") {
        // 记录符号链接
        symlinks.add("bash -> /data/data/com.termux/files/usr/bin/sh");
    } else {
        // 解压文件
        extractTo(STAGING_PREFIX + "/" + entry.name);
        
        // 设置可执行权限
        if (entry.name.startsWith("bin/")) {
            chmod(file, 0700);  // rwx------
        }
    }
}

// 4. 创建符号链接
for (symlink : symlinks) {
    Os.symlink(target, link);
}

// 5. 原子性移动到最终位置
STAGING_PREFIX.renameTo(PREFIX);
// /data/data/com.termux/files/usr-staging/ 
// → /data/data/com.termux/files/usr/
```

## 🗂️ Termux 文件系统布局

```
/data/data/com.termux/files/
├── home/                          # $HOME (~)
│   ├── .bashrc
│   ├── .profile
│   └── storage/                   # 外部存储链接
│       ├── shared -> /storage/emulated/0/
│       ├── downloads -> /storage/emulated/0/Download/
│       └── dcim -> /storage/emulated/0/DCIM/
│
└── usr/                           # $PREFIX
    ├── bin/                       # 可执行文件
    │   ├── bash
    │   ├── python
    │   ├── node
    │   └── ...
    ├── lib/                       # 共享库
    │   ├── libc.so
    │   ├── libpython3.so
    │   └── ...
    ├── include/                   # 头文件
    ├── share/                     # 共享数据
    ├── etc/                       # 配置
    ├── var/                       # 变量数据
    │   ├── lib/apt/               # APT 数据库
    │   └── log/                   # 日志
    └── tmp/                       # 临时文件
```

## 🔧 环境变量设置

Termux 通过环境变量让程序"以为"在标准 Linux 环境中：

```java
// TermuxShellEnvironment.java
public HashMap<String, String> getEnvironment() {
    HashMap<String, String> env = new HashMap<>();
    
    // 核心路径
    env.put("HOME", "/data/data/com.termux/files/home");
    env.put("PREFIX", "/data/data/com.termux/files/usr");
    env.put("TMPDIR", "/data/data/com.termux/files/usr/tmp");
    
    // PATH：让 shell 找到命令
    env.put("PATH", 
        "/data/data/com.termux/files/usr/bin:" +
        "/system/bin:" +
        "/system/xbin");
    
    // 库路径（Android 5/6 需要）
    if (isAndroid5or6) {
        env.put("LD_LIBRARY_PATH", 
            "/data/data/com.termux/files/usr/lib");
    }
    
    // Shell 配置
    env.put("SHELL", "/data/data/com.termux/files/usr/bin/bash");
    env.put("TERM", "xterm-256color");
    env.put("LANG", "en_US.UTF-8");
    
    // Android 特定
    env.put("ANDROID_DATA", "/data");
    env.put("ANDROID_ROOT", "/system");
    
    return env;
}
```

## 🏗️ 程序如何运行

### 示例：运行 Python

```bash
# 用户输入
$ python hello.py

# 实际执行过程
1. Shell 在 PATH 中查找 python
   → 找到 /data/data/com.termux/files/usr/bin/python

2. 执行 python 二进制文件
   → 这是一个 ARM64 Linux ELF 可执行文件
   → Android 的 Linux 内核直接加载执行

3. Python 需要加载共享库
   → 查找 libpython3.so
   → 在 /data/data/com.termux/files/usr/lib/ 找到
   → 动态链接器加载库

4. Python 读取 hello.py
   → 使用标准 Linux 文件 I/O
   → open("/data/data/com.termux/files/home/hello.py")

5. 输出到终端
   → write(stdout, "Hello World\n")
   → 通过 PTY 传递到 Termux 应用
```

## 🎯 关键技术点

### 1. 交叉编译

Termux 的所有程序都是**交叉编译**的：

```bash
# 在 x86_64 Linux 服务器上编译 ARM64 程序
./configure \
    --host=aarch64-linux-android \
    --prefix=/data/data/com.termux/files/usr \
    CC=aarch64-linux-android-clang

make
make install DESTDIR=/path/to/bootstrap
```

**编译目标：**
- ARM64 (aarch64) - 现代手机
- ARM32 (arm) - 老手机
- x86_64 - 模拟器
- x86 - 老模拟器

### 2. 路径修正

所有程序都被编译为使用 Termux 的路径：

```c
// 标准 Linux 程序
#define CONFIG_PATH "/etc/config"

// Termux 版本
#define CONFIG_PATH "/data/data/com.termux/files/usr/etc/config"
```

### 3. 动态链接器

```bash
# 查看程序依赖
$ readelf -d /data/data/com.termux/files/usr/bin/python

Dynamic section:
  NEEDED    libpython3.11.so
  NEEDED    libc.so
  RUNPATH   /data/data/com.termux/files/usr/lib
```

### 4. 包管理器 (APT)

```bash
# APT 配置
$ cat /data/data/com.termux/files/usr/etc/apt/sources.list
deb https://packages.termux.dev/apt/termux-main stable main

# 安装包
$ apt update
$ apt install vim

# APT 下载 .deb 包
# 解压到 $PREFIX
# 运行安装脚本
```

## 🔐 权限和安全

### Android 应用沙箱

```
每个 Android 应用都有：
- 独立的 Linux 用户 ID (UID)
- 独立的文件系统目录
- 独立的进程空间

Termux 应用：
UID: u0_a123
Home: /data/data/com.termux/
Processes: 只能访问自己的文件
```

### SELinux 限制

```bash
# Termux 进程的 SELinux 上下文
$ cat /proc/self/attr/current
u:r:untrusted_app:s0:c123,c256,c512,c768

# 限制：
- 不能访问其他应用的数据
- 不能修改系统文件
- 不能加载内核模块
- 不能使用某些系统调用
```

## 🚀 为什么这样设计？

### 优势

1. **性能**
   - 原生执行，无虚拟化开销
   - 直接使用 Android 的 Linux 内核

2. **兼容性**
   - 运行真正的 Linux 程序
   - 支持标准 Linux 工具链

3. **便携性**
   - 所有文件在应用目录
   - 卸载即清理

4. **安全性**
   - Android 沙箱隔离
   - 不需要 root 权限

### 限制

1. **不能访问系统文件**
   ```bash
   $ ls /system/
   Permission denied
   ```

2. **不能使用某些系统调用**
   ```bash
   $ mount /dev/sda1 /mnt
   Operation not permitted
   ```

3. **不能运行需要 root 的程序**
   ```bash
   $ sudo apt update
   sudo: effective uid is not 0
   ```

## 📊 对比其他方案

```
┌──────────────┬─────────┬──────────┬──────────┬──────────┐
│              │ Termux  │ UserLAnd │ Linux    │ Chroot   │
│              │         │          │ Deploy   │          │
├──────────────┼─────────┼──────────┼──────────┼──────────┤
│ 需要 Root    │   ❌    │    ❌    │    ✅    │    ✅    │
│ 性能         │  原生   │  PRoot   │   原生   │   原生   │
│ 完整 Linux   │   ❌    │    ✅    │    ✅    │    ✅    │
│ 易用性       │   ✅    │    ✅    │    ❌    │    ❌    │
│ 安全性       │   ✅    │    ✅    │    ❌    │    ❌    │
└──────────────┴─────────┴──────────┴──────────┴──────────┘
```

## 🎓 总结

Termux 的"Linux 环境"实际上是：

1. **利用 Android 的 Linux 内核**
   - 不需要虚拟化
   - 直接运行 Linux 程序

2. **提供 Linux 用户空间**
   - Bootstrap 包含基础工具
   - APT 提供更多软件包

3. **设置正确的环境**
   - 环境变量指向正确路径
   - 程序编译时使用 Termux 路径

4. **在应用沙箱内运行**
   - 安全隔离
   - 不需要 root

**核心理念：**
```
Termux 不是"在 Android 上运行 Linux"
而是"在 Android 的 Linux 内核上运行 Linux 用户空间"
```

这就像：
- Android 是房子的地基（Linux 内核）
- Termux 是在地基上搭建的房间（用户空间）
- 不需要再建一个地基（虚拟化）
