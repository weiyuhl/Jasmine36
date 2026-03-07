package com.lhzkml.jasmine.core.proot

import java.io.File

/**
 * PRoot + Alpine Linux 环境管理器。
 *
 * 提供完整的 Linux 命令行环境（apk 包管理、python、gcc 等），
 * 通过 PRoot 用户态 chroot 在 Android 上运行，无需 root 权限。
 *
 * @param filesDir 应用内部存储目录 (context.filesDir)
 * @param cacheDir 应用缓存目录 (context.cacheDir)
 */
class PRootEnvironment(
    filesDir: File,
    private val cacheDir: File
) {
    val paths = PRootPaths.from(filesDir)

    val isInstalled: Boolean
        get() = AlpineBootstrap.isInstalled(paths)

    /**
     * 安装 Alpine Linux 环境。
     * 下载 PRoot 二进制和 Alpine minirootfs，解压并配置。
     */
    suspend fun install(onProgress: (Float, String) -> Unit = { _, _ -> }) {
        AlpineBootstrap.install(paths, cacheDir, onProgress)
    }

    /**
     * 完全卸载环境，删除所有文件。
     */
    suspend fun uninstall() {
        AlpineBootstrap.uninstall(paths)
    }

    /**
     * 在 PRoot/Alpine 环境中执行命令。
     *
     * @param command Shell 命令（在 Alpine 的 /bin/sh 中执行）
     * @param workingDirectory 容器内工作目录，默认 /root
     * @param extraBindPaths 额外的宿主路径绑定 (hostPath -> guestPath)
     * @param environment 额外环境变量
     * @param timeoutSeconds 超时秒数
     * @param background 是否立即返回（后台执行）
     */
    suspend fun executeCommand(
        command: String,
        workingDirectory: String = "/root",
        extraBindPaths: List<Pair<String, String>> = emptyList(),
        environment: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 60,
        background: Boolean = false
    ): PRootResult {
        return PRootCommandExecutor.execute(
            paths = paths,
            command = command,
            workingDirectory = workingDirectory,
            extraBindPaths = extraBindPaths,
            environment = environment,
            timeoutSeconds = timeoutSeconds,
            background = background
        )
    }

    /**
     * 通过 apk 安装包。
     */
    suspend fun installPackage(packageName: String): PRootResult {
        return executeCommand("apk add --no-cache $packageName", timeoutSeconds = 120)
    }

    /**
     * 通过 apk 卸载包。
     */
    suspend fun removePackage(packageName: String): PRootResult {
        return executeCommand("apk del $packageName", timeoutSeconds = 60)
    }

    /**
     * 更新 apk 索引。
     */
    suspend fun updateIndex(): PRootResult {
        return executeCommand("apk update", timeoutSeconds = 60)
    }

    /**
     * 获取已安装包列表。
     */
    suspend fun listInstalledPackages(): List<String> {
        val result = executeCommand("apk list --installed 2>/dev/null | sort")
        if (result.exitCode != 0) return emptyList()
        return result.output.lines()
            .filter { it.isNotBlank() }
    }

    /**
     * 获取 Alpine 版本。
     */
    suspend fun getAlpineVersion(): String {
        val result = executeCommand("cat /etc/alpine-release 2>/dev/null")
        return result.output.trim().ifEmpty { "unknown" }
    }

    /**
     * 获取 PRoot 版本。
     */
    fun getPRootVersion(): String {
        return try {
            val process = ProcessBuilder(paths.prootBinary.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            output.lines().firstOrNull { it.contains("proot", ignoreCase = true) }?.trim()
                ?: output.trim().take(100)
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 获取 rootfs 占用磁盘空间（字节）。
     */
    fun getDiskUsageBytes(): Long {
        return paths.baseDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun formatDiskUsage(): String {
        val bytes = getDiskUsageBytes()
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    /**
     * 获取最新的安装日志文件。
     */
    fun getLatestLogFile(): File? = AlpineBootstrap.getLatestLogFile(paths)

    /**
     * 获取日志目录。
     */
    fun getLogDir(): File = File(paths.baseDir, "logs")
}
