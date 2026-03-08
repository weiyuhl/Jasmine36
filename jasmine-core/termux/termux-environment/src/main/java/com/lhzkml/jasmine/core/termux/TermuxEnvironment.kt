package com.lhzkml.jasmine.core.termux

import android.content.Context
import android.os.Build
import android.os.Environment
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Termux 环境管理器
 * 
 * 提供完整的 Linux 用户空间工具（bash, apt, python, git 等），
 * 直接在 Android 的 Linux 内核上运行，无虚拟化开销。
 * 
 * 使用方式：
 * ```kotlin
 * val termux = TermuxEnvironment(context)
 * 
 * // 检查是否已安装
 * if (!termux.isInstalled) {
 *     termux.install { progress, message ->
 *         updateUI(progress, message)
 *     }
 * }
 * 
 * // 执行命令
 * val result = termux.executeCommand("python3 --version")
 * println(result.output)
 * ```
 * 
 * @param context Android Context
 */
class TermuxEnvironment(
    private val context: Context
) {
    private val filesDir: File = context.filesDir
    private val cacheDir: File = context.cacheDir
    /** 路径配置 */
    val paths = TermuxPaths.from(filesDir)
    
    /** 检查 Bootstrap 是否已安装 */
    val isInstalled: Boolean
        get() = TermuxBootstrap.isInstalled(paths)
    
    /**
     * 安装 Bootstrap
     * 
     * @param onProgress 进度回调 (progress: 0.0-1.0, message: String)
     */
    suspend fun install(onProgress: (Float, String) -> Unit = { _, _ -> }) {
        TermuxBootstrap.install(paths, cacheDir, onProgress)
        writeEnvironmentToFile()
    }
    
    /**
     * 设置存储符号链接
     * 
     * 创建 ~/storage/ 目录下的符号链接，方便访问 Android 存储：
     * - ~/storage/shared -> /storage/emulated/0
     * - ~/storage/downloads -> /storage/emulated/0/Download
     * - ~/storage/dcim -> /storage/emulated/0/DCIM
     * - ~/storage/pictures -> /storage/emulated/0/Pictures
     * - ~/storage/music -> /storage/emulated/0/Music
     * - ~/storage/movies -> /storage/emulated/0/Movies
     * - ~/storage/podcasts -> /storage/emulated/0/Podcasts
     * - ~/storage/audiobooks -> /storage/emulated/0/Audiobooks (Android 10+)
     * - ~/storage/external-0 -> /storage/emulated/0/Android/data/[package]/files
     * - ~/storage/media-0 -> /storage/emulated/0/Android/media/[package]
     */
    suspend fun setupStorageSymlinks() = withContext(Dispatchers.IO) {
        try {
            log("Setting up storage symlinks...")
            
            val storageDir = paths.storageDir
            
            // 清空并重新创建 storage 目录
            if (storageDir.exists()) {
                storageDir.deleteRecursively()
            }
            storageDir.mkdirs()
            
            // 主存储根目录
            val sharedDir = Environment.getExternalStorageDirectory()
            createSymlink(sharedDir.absolutePath, File(storageDir, "shared").absolutePath)
            
            // 标准目录
            createSymlink(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
                File(storageDir, "documents").absolutePath
            )
            
            createSymlink(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                File(storageDir, "downloads").absolutePath
            )
            
            createSymlink(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
                File(storageDir, "dcim").absolutePath
            )
            
            createSymlink(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
                File(storageDir, "pictures").absolutePath
            )
            
            createSymlink(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath,
                File(storageDir, "music").absolutePath
            )
            
            createSymlink(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath,
                File(storageDir, "movies").absolutePath
            )
            
            createSymlink(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS).absolutePath,
                File(storageDir, "podcasts").absolutePath
            )
            
            // Android 10+ 的 Audiobooks 目录
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createSymlink(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS).absolutePath,
                    File(storageDir, "audiobooks").absolutePath
                )
            }
            
            // Android/data/[package]/files 目录
            val externalFilesDirs = context.getExternalFilesDirs(null)
            if (externalFilesDirs != null) {
                for (i in externalFilesDirs.indices) {
                    val dir = externalFilesDirs[i]
                    if (dir != null) {
                        val symlinkName = "external-$i"
                        createSymlink(dir.absolutePath, File(storageDir, symlinkName).absolutePath)
                    }
                }
            }
            
            // Android/media/[package] 目录
            val externalMediaDirs = context.externalMediaDirs
            if (externalMediaDirs != null) {
                for (i in externalMediaDirs.indices) {
                    val dir = externalMediaDirs[i]
                    if (dir != null) {
                        val symlinkName = "media-$i"
                        createSymlink(dir.absolutePath, File(storageDir, symlinkName).absolutePath)
                    }
                }
            }
            
            log("Storage symlinks created successfully")
        } catch (e: Exception) {
            log("Failed to setup storage symlinks: ${e.message}")
            throw e
        }
    }
    
    /**
     * 写入环境变量到文件
     * 
     * 创建 ~/.termux/shell-environment 文件，包含必要的环境变量
     */
    private suspend fun writeEnvironmentToFile() = withContext(Dispatchers.IO) {
        try {
            log("Writing environment to file...")
            
            val termuxDir = File(paths.homeDir, ".termux")
            termuxDir.mkdirs()
            
            val envFile = File(termuxDir, "shell-environment")
            
            val envContent = buildString {
                appendLine("# Termux Shell Environment")
                appendLine("# This file is sourced by bash on startup")
                appendLine()
                appendLine("export PREFIX='${paths.prefixDir.absolutePath}'")
                appendLine("export HOME='${paths.homeDir.absolutePath}'")
                appendLine("export TMPDIR='${paths.tmpDir.absolutePath}'")
                appendLine("export PATH='\$PREFIX/bin:\$PATH'")
                appendLine("export LD_LIBRARY_PATH='\$PREFIX/lib'")
                appendLine("export LANG='en_US.UTF-8'")
                appendLine("export TERM='xterm-256color'")
                appendLine()
            }
            
            envFile.writeText(envContent)
            log("Environment file written successfully")
        } catch (e: Exception) {
            log("Failed to write environment file: ${e.message}")
            // 不抛出异常，因为这不是关键功能
        }
    }
    
    /**
     * 创建符号链接
     */
    private fun createSymlink(target: String, link: String) {
        try {
            Os.symlink(target, link)
            log("Created symlink: $link -> $target")
        } catch (e: Exception) {
            log("Warning: Failed to create symlink $link -> $target: ${e.message}")
        }
    }
    
    /**
     * 写入日志
     */
    private fun log(message: String) {
        try {
            val logFile = File(paths.logDir, "termux_environment.log")
            val timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                java.util.Locale.US
            ).format(java.util.Date())
            logFile.appendText("[$timestamp] $message\n")
        } catch (_: Exception) {
            // 忽略日志错误
        }
    }
    
    /**
     * 执行命令
     * 
     * @param command 要执行的命令
     * @param workingDirectory 工作目录（可选）
     * @param environment 额外的环境变量
     * @param timeoutSeconds 超时时间（秒）
     * @param background 是否后台运行
     * @return 命令执行结果
     */
    suspend fun executeCommand(
        command: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 60,
        background: Boolean = false
    ): TermuxResult {
        return TermuxCommandExecutor.execute(
            paths = paths,
            command = command,
            workingDirectory = workingDirectory,
            environment = environment,
            timeoutSeconds = timeoutSeconds,
            background = background
        )
    }
    
    /**
     * 安装软件包
     * 
     * @param packageName 包名
     * @return 执行结果
     */
    suspend fun installPackage(packageName: String): TermuxResult {
        return executeCommand(
            command = "apt install -y $packageName",
            timeoutSeconds = 300 // 5 分钟
        )
    }
    
    /**
     * 卸载软件包
     * 
     * @param packageName 包名
     * @return 执行结果
     */
    suspend fun removePackage(packageName: String): TermuxResult {
        return executeCommand(
            command = "apt remove -y $packageName",
            timeoutSeconds = 120
        )
    }
    
    /**
     * 更新软件包索引
     * 
     * @return 执行结果
     */
    suspend fun updatePackageIndex(): TermuxResult {
        return executeCommand(
            command = "apt update",
            timeoutSeconds = 120
        )
    }
    
    /**
     * 获取已安装的软件包列表
     * 
     * @return 软件包列表
     */
    suspend fun getInstalledPackages(): List<String> {
        val result = executeCommand("dpkg -l | grep '^ii' | awk '{print \$2}'")
        if (result.exitCode != 0) return emptyList()
        return result.output.lines()
            .filter { it.isNotBlank() }
            .sorted()
    }
}
