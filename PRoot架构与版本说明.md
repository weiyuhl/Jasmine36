# PRoot 架构与版本说明

本文档说明 Jasmine 项目中 Linux 环境（PRoot + Alpine）的架构、版本情况，以及与 Termux 的对比。

---

## 一、版本确认：Jasmine 使用的是否为旧版？

**结论：Jasmine 使用的 PRoot 5.4.0 是上游最新版本，并非旧版。**

### 1.1 上游 PRoot 发布情况

| 版本 | 发布日期 | 说明 |
|------|----------|------|
| **v5.4.0** | 2023-05-13 | **当前最新**（proot-me/proot） |
| v5.3.1 | 2022-04-24 | 上一版 |
| v5.3.0 | 2022-01-04 | 更早 |

截至 2026 年 3 月，[proot-me/proot](https://github.com/proot-me/proot) 官方仓库**未发布** 5.5、5.6 或更高版本。5.4.0 为 Latest 标签。

### 1.2 Jasmine 当前配置

| 项目 | 值 |
|------|-----|
| PRoot 来源 | Alpine `proot-static-5.4.0-r2.apk` |
| 下载地址 | `https://dl-cdn.alpinelinux.org/alpine/edge/community/aarch64/proot-static-5.4.0-r2.apk` |
| 二进制路径 | APK 内 `usr/bin/proot.static` |
| 打包形式 | 以 `libproot.so` 形式放入 APK jniLibs |
| Alpine 包大小 | 约 121 KiB（压缩）/ 393 KiB（安装后） |

### 1.3 关于「214KB 旧版」的说明

若在其他文档或讨论中看到「PRoot 旧版 214KB」，可能指：

- 早期 Termux 或其它项目使用的旧版 PRoot
- 不同架构/编译选项下的体积差异
- 与 Jasmine 当前使用的 5.4.0 无直接对应关系

Jasmine 当前使用的 5.4.0 与上游 proot-me 最新一致。但 Termux 使用自维护 fork（5.1.107-70），含 clone3、memfd、TCSETS2 等 Android 补丁，若遇兼容问题可考虑改用 Termux 包。

---

## 二、Termux 仓库与包信息（2026-03 查阅）

### 2.1 termux/proot

- **仓库**：<https://github.com/termux/proot>
- **说明**：PRoot 的 Termux 维护 fork，基于 ptrace 的 chroot 实现
- **Stars**：约 1,016
- **发布**：无 GitHub Releases，不提供预编译包。获取方式：**自行编译源码**，或从 [packages.termux.dev](https://packages.termux.dev/apt/termux-main/pool/main/p/proot/) 的 `.deb` 包中**解压提取**二进制
- **近期提交**（2026 年）：
  - 2026-02-21：AArch64 SP 16 字节对齐
  - 2026-01-18：clone3 支持
  - 2026-01-06：memfd_create 限制、包安装 bad file descriptor 修复、NDK r29 构建
  - 2025-10-18：TCSETS2/TCGETS2/TCSETSF2 兼容
- **Android 相关补丁**：memfd_create、TCSETS2、clone3、SysV 共享内存、link2symlink、QEMU 等

### 2.2 termux-app

- **仓库**：<https://github.com/termux/termux-app>
- **说明**：Termux 主应用
- **Stars**：约 51,598

### 2.3 Termux proot 包

- **包索引**：<https://packages.termux.dev/apt/termux-main/pool/main/p/proot/>
- **包版本**：`proot_5.1.107-70`
- **构建时间**：2026-01-18
- **体积**（aarch64）：约 85 KB（.deb）
- **架构**：aarch64、arm、i686、x86_64

**说明**：Termux 使用自维护 fork，版本号 5.1.107-70 为 Termux 内部编号，与上游 proot-me 5.4.0 无直接对应关系。该 fork 包含大量 Android 专用补丁，持续维护中。

---

## 三、Jasmine vs Termux 架构对比

| 维度 | Termux（成熟方案） | Jasmine（当前） |
|------|-------------------|-----------------|
| **PRoot 来源** | [termux/proot](https://github.com/termux/proot) fork，5.1.107-70 | [proot-me/proot](https://github.com/proot-me/proot) 上游 5.4.0（Alpine proot-static） |
| **Android 补丁** | 有（clone3、memfd、TCSETS2、NDK r29 等） | 无，依赖上游通用实现 |
| **rootfs** | Android 专门优化版（补丁、兼容层） | Alpine 官方 minirootfs，无定制补丁 |
| **发行版支持** | 多发行版：Ubuntu、Debian、Alpine 等 | 仅 Alpine |
| **管理方式** | proot-distro 动态管理，可安装/切换发行版 | 内置固定，单一 Alpine 实例 |
| **syscall 处理** | 不完整，依赖兼容层和补丁 | 不完整，用 `--no-scripts` 规避 fchdir 等 |
| **兼容层** | 完整（fake /proc、bind、环境变量等） | 参考 proot-distro 实现 |
| **rootfs** | Android 专门优化版（补丁、兼容层） | Alpine 官方 minirootfs，无定制补丁 |
| **发行版支持** | 多发行版：Ubuntu、Debian、Alpine 等 | 仅 Alpine |
| **管理方式** | proot-distro 动态管理，可安装/切换发行版 | 内置固定，单一 Alpine 实例 |
| **syscall 处理** | 不完整，依赖兼容层和补丁 | 不完整，用 `--no-scripts` 规避 fchdir 等 |
| **兼容层** | 完整（fake /proc、bind、环境变量等） | 参考 proot-distro 实现 |

---

## 四、Jasmine 当前实现要点

### 4.1 PRoot 来源

- 从 Alpine 官方 `proot-static` 包提取 `proot.static`
- 以 `libproot.so` 形式打包进 APK 的 jniLibs
- 版本：5.4.0-r2（r2 为 Alpine 修订号）

### 4.2 rootfs

- 使用 Alpine 官方 `alpine-minirootfs-3.21.3-aarch64.tar.gz`
- 无 Android 专用补丁

### 4.3 兼容层（参考 Termux proot-distro）

- 核心 bind：`/dev`、`/proc`、`/sys` 等
- fake /proc：loadavg、stat、uptime、version、vmstat 等
- 环境变量：`/usr/bin/env -i` 等
- 内核版本伪装：`--kernel-release=6.17.0-PRoot-Distro`

### 4.4 规避策略

- `apk add --no-scripts`：跳过 post-install 脚本，减少 fchdir 等 syscall 问题
- `filterProotNoise`：过滤 proot 的 warning/info 输出

---

## 五、若需进一步升级的方向

1. **多发行版**：引入类似 proot-distro 的发行版管理，支持 Ubuntu、Debian 等
2. **rootfs 优化**：对 rootfs 做 Android 适配补丁
3. **PRoot 来源**：评估 [termux/proot](https://github.com/termux/proot) 或 [packages.termux.dev](https://packages.termux.dev/apt/termux-main/pool/main/p/proot/) 的包，获取 clone3、memfd、TCSETS2 等 Android 补丁。需自行编译或从 `.deb` 中解压提取（无预编译 Release）
4. **syscall 兼容**：在无法改内核的前提下，继续通过规避策略和兼容层减少问题

---

*文档更新时间：2026-03*
