# Bootstrap 文件说明

## 📦 文件列表

本目录包含 Termux 的 Bootstrap 文件，这些是 Termux 能够运行的核心。

```
bootstrap-aarch64.zip    29.13 MB  (ARM64)
bootstrap-arm.zip        26.19 MB  (ARM32)
bootstrap-i686.zip       28.35 MB  (x86)
bootstrap-x86_64.zip     28.99 MB  (x86_64)
```

## 🎯 文件用途

### 架构说明

- **bootstrap-aarch64.zip** - ARM64 架构
  - 适用于：现代 Android 手机（2015年后）
  - 处理器：高通骁龙、联发科天玑、华为麒麟等

- **bootstrap-arm.zip** - ARM32 架构
  - 适用于：老旧 Android 手机（2015年前）
  - 处理器：老款 ARM 处理器

- **bootstrap-i686.zip** - x86 架构
  - 适用于：老旧 Android 模拟器
  - 处理器：Intel Atom（32位）

- **bootstrap-x86_64.zip** - x86_64 架构
  - 适用于：现代 Android 模拟器
  - 处理器：Intel/AMD（64位）

## 📋 文件内容

每个 ZIP 文件包含完整的 Linux 用户空间工具：

```
bootstrap-aarch64.zip
├── bin/              # 可执行文件
│   ├── bash          # Shell
│   ├── ls            # 列出文件
│   ├── cat           # 查看文件
│   ├── grep          # 搜索
│   ├── apt           # 包管理器
│   ├── python        # Python 解释器
│   └── ...           # 数百个工具
├── lib/              # 共享库
│   ├── libc.so       # C 标准库
│   ├── libssl.so     # SSL 库
│   ├── libpython.so  # Python 库
│   └── ...
├── etc/              # 配置文件
│   ├── bash.bashrc
│   └── apt/sources.list
├── usr/              # 用户程序
├── var/              # 变量数据
└── SYMLINKS.txt      # 符号链接列表
```

## 🔍 查看内容

### 列出文件
```bash
unzip -l bootstrap-aarch64.zip | less
```

### 解压查看
```bash
unzip bootstrap-aarch64.zip -d bootstrap-extracted/
cd bootstrap-extracted/
ls -la
```

### 查看特定文件
```bash
unzip -p bootstrap-aarch64.zip bin/bash | file -
unzip -p bootstrap-aarch64.zip SYMLINKS.txt | head -20
```

## 🛠️ 技术细节

### 编译信息

- **版本**: 2026.02.12-r1+apt.android-7
- **包管理器**: APT (Advanced Package Tool)
- **目标系统**: Android 7+
- **编译工具链**: Android NDK
- **下载来源**: https://github.com/termux/termux-packages/releases

### 文件特点

1. **交叉编译**
   - 在 x86_64 Linux 服务器上编译
   - 目标平台：ARM/x86 Android

2. **路径硬编码**
   - 所有路径指向 `/data/data/com.termux/files/usr/`
   - 不能直接在标准 Linux 上使用

3. **依赖关系**
   - 使用 Android Bionic libc
   - 依赖 Android 系统库

4. **权限设置**
   - bin/ 目录下文件：0700 (rwx------)
   - 其他文件：0644 (rw-r--r--)

## 🔄 使用流程

### 在 Termux 编译中

1. **编译时下载**
   ```bash
   ./gradlew downloadBootstraps
   ```

2. **嵌入到 APK**
   - 通过汇编 `.incbin` 指令嵌入
   - 编译为 `libtermux-bootstrap.so`
   - 打包到 APK 的 `lib/` 目录

3. **运行时解压**
   - 用户首次启动 Termux
   - 从 `.so` 文件读取 ZIP
   - 解压到 `/data/data/com.termux/files/usr/`

### 手动使用

如果你想在其他地方使用这些文件：

```bash
# 1. 解压
unzip bootstrap-aarch64.zip -d /path/to/termux-root/

# 2. 设置环境变量
export PREFIX=/path/to/termux-root
export PATH=$PREFIX/bin:$PATH
export LD_LIBRARY_PATH=$PREFIX/lib

# 3. 运行
$PREFIX/bin/bash
```

**注意**: 由于路径硬编码，可能无法正常工作。

## 📊 文件对比

### 大小差异

```
ARM64:   29.13 MB  (最大，支持最多指令集)
ARM32:   26.19 MB  (最小，指令集较少)
x86:     28.35 MB
x86_64:  28.99 MB
```

### 内容差异

- 核心工具相同
- 二进制文件架构不同
- 共享库架构不同
- 配置文件相同

## 🔐 安全性

### 校验和

每个文件都有 SHA-256 校验和：

```
aarch64: ea2aeba8819e517db711f8c32369e89e7c52cee73e07930ff91185e1ab93f4f3
arm:     a38f4d3b2f735f83be2bf54eff463e86dc32a3e2f9f861c1557c4378d249c018
i686:    f5bc0b025b9f3b420b5fcaeefc064f888f5f22a0d6fd7090f4aac0c33eb3555b
x86_64:  b7fd0f2e3a4de534be3144f9f91acc768630fc463eaf134ab2e64c545e834f7a
```

### 验证方法

```bash
# Windows PowerShell
Get-FileHash bootstrap-aarch64.zip -Algorithm SHA256

# Linux/Mac
sha256sum bootstrap-aarch64.zip
```

## 📚 相关文档

- **03-Bootstrap集成方式.md** - 详细的集成原理
- **02-Linux环境实现原理.md** - Bootstrap 的作用
- **04-工作流程实例.md** - 安装和使用流程

## 🎓 学习建议

1. **解压一个文件**
   ```bash
   unzip bootstrap-aarch64.zip -d extracted/
   ```

2. **浏览目录结构**
   ```bash
   cd extracted/
   tree -L 2
   ```

3. **查看可执行文件**
   ```bash
   file bin/bash
   readelf -h bin/bash
   ```

4. **查看依赖关系**
   ```bash
   readelf -d bin/bash | grep NEEDED
   ```

5. **查看符号链接**
   ```bash
   cat SYMLINKS.txt
   ```

## ⚠️ 注意事项

1. **不要修改这些文件**
   - 这些是编译时使用的原始文件
   - 修改可能导致编译失败

2. **不要在标准 Linux 上使用**
   - 这些文件专为 Android 编译
   - 路径和依赖都是 Android 特定的

3. **版本匹配**
   - 确保 Bootstrap 版本与 Termux 版本匹配
   - 不同版本可能不兼容

## 🔗 下载地址

如果需要重新下载或获取其他版本：

```
https://github.com/termux/termux-packages/releases
```

查找以 `bootstrap-` 开头的 release。

## 📝 更新记录

- **2026.02.12-r1** - 当前版本
  - 支持 Android 7+
  - 使用 APT 包管理器
  - 包含最新的工具和库

---

**这些文件是 Termux 的核心，理解它们就理解了 Termux 的本质！** 🚀
