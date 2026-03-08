# Termux Environment

Termux 环境管理模块，提供 Linux 用户空间工具支持。

## 模块结构

```
termux-environment/
├── src/main/
│   ├── java/com/lhzkml/jasmine/core/termux/
│   │   ├── TermuxEnvironment.kt       # 环境管理器
│   │   ├── TermuxBootstrap.kt         # Bootstrap 安装
│   │   ├── TermuxCommandExecutor.kt   # 命令执行
│   │   └── TermuxPaths.kt             # 路径管理
│   └── cpp/
│       ├── termux-bootstrap.c         # JNI 接口
│       ├── termux-bootstrap-zip.S     # Bootstrap 嵌入（汇编）
│       └── Android.mk                 # NDK 构建配置
└── build.gradle.kts                   # 构建配置（含自动下载）
```

## Bootstrap 自动下载

Bootstrap 文件在编译时自动从 GitHub 下载，不包含在源代码中。

**版本**: 2026.02.12-r1+apt.android-7  
**大小**: 约 112 MB（4 个架构）  
**来源**: https://github.com/termux/termux-packages/releases

编译时会自动：
1. 检查文件是否存在
2. 验证 SHA-256 校验和
3. 如需要，从 GitHub 下载
4. 再次验证校验和

## 编译

```bash
# 编译模块（首次会自动下载 Bootstrap）
./gradlew :jasmine-core:termux:termux-environment:build

# 手动下载 Bootstrap
./gradlew :jasmine-core:termux:termux-environment:downloadBootstraps

# 清理
./gradlew :jasmine-core:termux:termux-environment:clean
```

## 使用

```kotlin
val termux = TermuxEnvironment(filesDir, cacheDir)

// 安装
if (!termux.isInstalled) {
    termux.install { progress, message -> }
}

// 执行命令
val result = termux.executeCommand("python3 --version")

// 包管理
termux.installPackage("curl")
termux.removePackage("curl")
val packages = termux.getInstalledPackages()
```

## 依赖

- Android NDK
- Kotlin Coroutines
