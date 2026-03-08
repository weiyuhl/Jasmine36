# Bootstrap 集成方式详解

## 🎯 答案：Bootstrap 直接嵌入在 APK 中！

Bootstrap **不是**安装后下载的，而是在**编译 APK 时就嵌入进去**的！

## 📦 完整流程

### 阶段 1：编译时（开发者构建 APK）

```
1. Gradle 构建开始
   ↓
2. 执行 downloadBootstraps() 任务
   ↓
3. 从 GitHub 下载 bootstrap ZIP 文件
   ├── bootstrap-aarch64.zip  (ARM64, ~30MB)
   ├── bootstrap-arm.zip      (ARM32, ~25MB)
   ├── bootstrap-x86_64.zip   (x86_64, ~30MB)
   └── bootstrap-i686.zip     (x86, ~25MB)
   ↓
4. 保存到 app/src/main/cpp/ 目录
   ↓
5. 编译 termux-bootstrap-zip.S (汇编文件)
   → 使用 .incbin 指令将 ZIP 文件嵌入到二进制中
   ↓
6. 编译 termux-bootstrap.c
   → 提供 getZip() JNI 方法
   ↓
7. 生成 libtermux-bootstrap.so
   → 这个 .so 文件包含了完整的 bootstrap ZIP！
   ↓
8. 打包到 APK
   APK 结构：
   ├── classes.dex
   ├── resources.arsc
   ├── lib/
   │   ├── arm64-v8a/
   │   │   └── libtermux-bootstrap.so  ← 包含 bootstrap-aarch64.zip
   │   ├── armeabi-v7a/
   │   │   └── libtermux-bootstrap.so  ← 包含 bootstrap-arm.zip
   │   ├── x86_64/
   │   │   └── libtermux-bootstrap.so  ← 包含 bootstrap-x86_64.zip
   │   └── x86/
   │       └── libtermux-bootstrap.so  ← 包含 bootstrap-i686.zip
   └── ...
```

### 阶段 2：运行时（用户首次启动 Termux）

```
1. 用户打开 Termux
   ↓
2. TermuxActivity.onCreate()
   ↓
3. 检查 /data/data/com.termux/files/usr/ 是否存在
   → 不存在！需要安装
   ↓
4. TermuxInstaller.setupBootstrapIfNeeded()
   ↓
5. 调用 JNI 方法 getZip()
   ↓
6. Native 代码从内存中返回 bootstrap ZIP
   byte[] zipBytes = TermuxInstaller.getZip();
   ↓
7. 解压 ZIP 到应用目录
   ↓
8. 完成！无需网络下载
```

## 🔍 技术细节

### 1. 汇编代码嵌入 ZIP

```asm
# termux-bootstrap-zip.S

.global blob          # 导出 blob 符号
.global blob_size     # 导出 blob_size 符号
.section .rodata      # 只读数据段

blob:
#if defined __aarch64__
    .incbin "bootstrap-aarch64.zip"  # 直接包含二进制文件！
#elif defined __arm__
    .incbin "bootstrap-arm.zip"
#elif defined __x86_64__
    .incbin "bootstrap-x86_64.zip"
#elif defined __i686__
    .incbin "bootstrap-i686.zip"
#endif

1:
blob_size:
    .int 1b - blob    # 计算 ZIP 文件大小
```

**关键点：**
- `.incbin` 指令直接将文件内容嵌入到编译后的二进制中
- 不同架构使用不同的 ZIP 文件
- 编译后，ZIP 数据就在 `.so` 文件的 `.rodata` 段中

### 2. C 代码读取 ZIP

```c
// termux-bootstrap.c

extern jbyte blob[];      // 引用汇编中定义的 blob
extern int blob_size;     // 引用汇编中定义的 blob_size

JNIEXPORT jbyteArray JNICALL 
Java_com_termux_app_TermuxInstaller_getZip(JNIEnv *env, jobject This) {
    // 创建 Java byte 数组
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    
    // 将 blob 数据复制到 Java 数组
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
    
    return ret;  // 返回包含 ZIP 的 byte[]
}
```

### 3. Gradle 构建配置

```groovy
// app/build.gradle

// 下载 bootstrap 的函数
def downloadBootstrap(String arch, String expectedChecksum, String version) {
    def localUrl = "src/main/cpp/bootstrap-" + arch + ".zip"
    def file = new File(projectDir, localUrl)
    
    // 如果文件已存在且校验和正确，跳过下载
    if (file.exists()) {
        def checksum = calculateSHA256(file)
        if (checksum == expectedChecksum) {
            return  // 文件已存在，无需下载
        }
    }
    
    // 从 GitHub 下载
    def remoteUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-" + version + "/bootstrap-" + arch + ".zip"
    
    logger.quiet("Downloading " + remoteUrl + " ...")
    
    // 下载并验证校验和
    downloadAndVerify(remoteUrl, file, expectedChecksum)
}

// 下载所有架构的 bootstrap
task downloadBootstraps() {
    doLast {
        def version = "2026.02.12-r1+apt.android-7"
        downloadBootstrap("aarch64", "ea2aeba8...", version)
        downloadBootstrap("arm", "a38f4d3b...", version)
        downloadBootstrap("i686", "f5bc0b02...", version)
        downloadBootstrap("x86_64", "b7fd0f2e...", version)
    }
}

// 确保在编译前下载 bootstrap
afterEvaluate {
    android.applicationVariants.all { variant ->
        variant.javaCompileProvider.get().dependsOn(downloadBootstraps)
    }
}
```

## 📊 APK 大小影响

```
Termux APK 大小分析：

Universal APK (包含所有架构):
├── 代码和资源: ~10 MB
├── arm64-v8a bootstrap: ~30 MB
├── armeabi-v7a bootstrap: ~25 MB
├── x86_64 bootstrap: ~30 MB
└── x86 bootstrap: ~25 MB
总计: ~120 MB

架构特定 APK (只包含一个架构):
├── 代码和资源: ~10 MB
└── 对应架构 bootstrap: ~30 MB
总计: ~40 MB
```

**这就是为什么：**
- F-Droid 只提供 Universal APK (~180MB)
- GitHub Releases 提供架构特定 APK (~40MB 每个)

## 🎭 为什么这样设计？

### 优势

1. **无需网络**
   - 首次启动不需要下载
   - 在没有网络的环境也能使用
   - 安装即可用

2. **速度快**
   - 从内存直接读取
   - 解压比下载快得多

3. **可靠性**
   - 不依赖外部服务器
   - 不会因为网络问题失败

4. **安全性**
   - Bootstrap 在编译时验证
   - 用户无法被中间人攻击

### 劣势

1. **APK 体积大**
   - Universal APK 约 180MB
   - 但架构特定 APK 只有 40MB

2. **更新麻烦**
   - 更新 bootstrap 需要发布新版本 APK
   - 不能独立更新 bootstrap

## 🔄 对比其他方案

### 方案 A：嵌入 APK（Termux 采用）

```
优点：
✅ 无需网络
✅ 速度快
✅ 可靠
✅ 安全

缺点：
❌ APK 体积大
❌ 更新需要发布新 APK
```

### 方案 B：首次启动下载

```
优点：
✅ APK 体积小
✅ 可以独立更新 bootstrap

缺点：
❌ 需要网络
❌ 首次启动慢
❌ 可能下载失败
❌ 需要处理下载进度、重试等
```

### 方案 C：混合方案

```
优点：
✅ APK 包含最小 bootstrap
✅ 可选下载完整版

缺点：
❌ 复杂度高
❌ 需要维护两套 bootstrap
```

## 💡 实际体验

```
用户视角：

1. 从 F-Droid/GitHub 下载 APK
   → 等待下载 180MB (Universal) 或 40MB (架构特定)

2. 安装 APK
   → 几秒钟

3. 首次打开 Termux
   → 显示 "Installing..." 进度条
   → 解压 bootstrap (约 5-10 秒)
   → 完成！

4. 后续打开
   → 立即可用
```

## 🎓 总结

**Bootstrap 是直接嵌入在 APK 中的！**

流程：
1. **编译时**：从 GitHub 下载 → 嵌入到 .so 文件 → 打包到 APK
2. **运行时**：从 .so 文件读取 → 解压到应用目录 → 完成

这就是为什么：
- Termux APK 比较大（40-180MB）
- 首次启动需要几秒钟解压
- 但之后使用不需要网络
- 安装即可用，非常可靠

这是一个**空间换时间和可靠性**的设计决策！
