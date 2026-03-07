package com.lhzkml.jasmine.core.proot

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val cacheDir: File,
    externalDir: File? = null,
    nativeLibDir: File? = null
) {
    val paths = PRootPaths.from(filesDir, externalDir, nativeLibDir)

    private val runtimeLogFile: File by lazy {
        paths.logDir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        File(paths.logDir, "runtime_$ts.log")
    }

    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$ts] $msg\n"
        try { runtimeLogFile.appendText(line) } catch (_: Exception) {}
    }

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
        log("exec: $command")
        val result = PRootCommandExecutor.execute(
            paths = paths,
            command = command,
            workingDirectory = workingDirectory,
            extraBindPaths = extraBindPaths,
            environment = environment,
            timeoutSeconds = timeoutSeconds,
            background = background
        )
        log("exec result: exit=${result.exitCode}, output=${result.output.take(500)}")
        return result.copy(output = filterProotNoise(result.output))
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
        return filterProotNoise(result.output).lines()
            .filter { it.isNotBlank() }
    }

    /**
     * 获取 Alpine 版本。
     */
    suspend fun getAlpineVersion(): String {
        val result = executeCommand("cat /etc/alpine-release 2>/dev/null")
        val clean = filterProotNoise(result.output).trim()
        return clean.ifEmpty { "unknown" }
    }

    /**
     * 获取 PRoot 版本。
     */
    fun getPRootVersion(): String {
        return try {
            val bin = paths.prootBinary
            log("getPRootVersion: path=${bin.absolutePath}, exists=${bin.exists()}, size=${bin.length()}, canExec=${bin.canExecute()}")
            val pb = ProcessBuilder(bin.absolutePath, "-V")
                .redirectErrorStream(true)
            val pbEnv = pb.environment()
            if (paths.prootLoader.exists()) pbEnv["PROOT_LOADER"] = paths.prootLoader.absolutePath
            if (paths.prootLoader32.exists()) pbEnv["PROOT_LOADER_32"] = paths.prootLoader32.absolutePath
            val ldPaths = mutableListOf<String>()
            if (paths.libSearchDir.exists()) ldPaths.add(paths.libSearchDir.absolutePath)
            if (paths.nativeLibDir?.exists() == true) ldPaths.add(paths.nativeLibDir.absolutePath)
            if (ldPaths.isNotEmpty()) pbEnv["LD_LIBRARY_PATH"] = ldPaths.joinToString(":")
            val process = pb.start()
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            val versionLine = output.lines()
                .firstOrNull { it.trim().matches(Regex("^\\d+\\.\\d+.*")) }
                ?.trim()
            val ver = versionLine
                ?: output.lines().firstOrNull { it.contains("proot", ignoreCase = true) && !it.contains("Visit") && !it.contains("help") }?.trim()
                ?: output.trim().lines().firstOrNull()?.trim()?.take(50)
                ?: "unknown"
            log("getPRootVersion: $ver")
            ver
        } catch (e: Exception) {
            log("getPRootVersion error: ${e.message}")
            "unknown: ${e.message?.take(100)}"
        }
    }

    companion object {
        fun filterProotNoise(output: String): String {
            return output.lines()
                .filter { line ->
                    !line.startsWith("proot warning:") &&
                    !line.startsWith("proot info:") &&
                    !line.startsWith("proot error:") &&
                    !line.contains("can't sanitize binding")
                }
                .joinToString("\n")
        }
    }

    /**
     * 获取 rootfs 占用磁盘空间（字节）。
     */
    fun getDiskUsageBytes(): Long {
        return try {
            paths.baseDir.walkTopDown()
                .filter {
                    try { it.isFile } catch (_: Exception) { false }
                }
                .sumOf {
                    try { it.length() } catch (_: Exception) { 0L }
                }
        } catch (_: Exception) { 0L }
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
    fun getLogDir(): File = paths.logDir
}
