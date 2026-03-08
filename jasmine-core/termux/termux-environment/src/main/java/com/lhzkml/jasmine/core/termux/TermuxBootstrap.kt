package com.lhzkml.jasmine.core.termux

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Termux Bootstrap 安装器
 * 
 * 完整实现 Termux 官方的安装流程：
 * 1. 解压到 staging 目录
 * 2. 处理 SYMLINKS.txt 创建符号链接
 * 3. 设置完整的可执行权限
 * 4. 原子性地移动到最终目录
 */
object TermuxBootstrap {
    
    /** 从 JNI 层获取嵌入的 Bootstrap ZIP */
    private external fun getZip(): ByteArray
    
    init {
        System.loadLibrary("termux-bootstrap")
    }
    
    /**
     * 检查 Bootstrap 是否已安装
     */
    fun isInstalled(paths: TermuxPaths): Boolean {
        return paths.prefixDir.exists() && 
               File(paths.prefixDir, "bin/bash").exists() &&
               File(paths.prefixDir, "bin/sh").exists()
    }
    
    /**
     * 安装 Bootstrap（完整实现）
     * 
     * 完全按照 Termux 官方流程：
     * 1. 删除旧的 staging 和 prefix 目录
     * 2. 创建新的 staging 目录
     * 3. 解压 ZIP 到 staging 目录
     * 4. 处理 SYMLINKS.txt 创建符号链接
     * 5. 设置可执行权限
     * 6. 原子性地 rename 到最终目录
     * 
     * @param paths Termux 路径配置
     * @param cacheDir 缓存目录
     * @param onProgress 进度回调 (progress: 0.0-1.0, message: String)
     */
    suspend fun install(
        paths: TermuxPaths,
        cacheDir: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        try {
            log(paths, "=== Termux Bootstrap Install Start ===")
            log(paths, "prefixDir: ${paths.prefixDir.absolutePath}")
            log(paths, "stagingDir: ${paths.stagingPrefixDir.absolutePath}")
            
            // Step 1: 删除旧的 staging 和 prefix 目录
            onProgress(0.05f, "正在清理旧文件...")
            log(paths, "Deleting old staging directory...")
            deleteDirectory(paths.stagingPrefixDir)
            
            log(paths, "Deleting old prefix directory...")
            deleteDirectory(paths.prefixDir)
            
            // Step 2: 创建必要的目录
            // 注意：只创建 staging 目录，不创建 prefix（因为后面要 rename staging 到 prefix）
            // home, tmp, log 等目录也不在 prefix 内，所以可以创建
            onProgress(0.1f, "正在创建目录...")
            paths.stagingPrefixDir.mkdirs()
            paths.homeDir.mkdirs()
            paths.tmpDir.mkdirs()
            paths.logDir.mkdirs()
            
            // Step 3: 从 JNI 层获取 ZIP 数据
            onProgress(0.15f, "正在读取 Bootstrap...")
            log(paths, "Reading Bootstrap ZIP from JNI...")
            val zipBytes = getZip()
            log(paths, "Bootstrap ZIP size: ${zipBytes.size} bytes")
            
            // Step 4: 解压到 staging 目录并处理 SYMLINKS.txt
            onProgress(0.2f, "正在解压 Bootstrap...")
            log(paths, "Extracting to staging directory...")
            
            val symlinks = mutableListOf<Pair<String, String>>()
            val buffer = ByteArray(8096)
            var fileCount = 0
            
            ZipInputStream(zipBytes.inputStream()).use { zipInput ->
                var zipEntry = zipInput.nextEntry
                
                while (zipEntry != null) {
                    val zipEntryName = zipEntry.name
                    
                    // 处理 SYMLINKS.txt
                    if (zipEntryName == "SYMLINKS.txt") {
                        log(paths, "Processing SYMLINKS.txt...")
                        BufferedReader(InputStreamReader(zipInput)).use { reader ->
                            var line = reader.readLine()
                            while (line != null) {
                                val parts = line.split("←")
                                if (parts.size == 2) {
                                    val oldPath = parts[0].trim()
                                    val newPath = File(paths.stagingPrefixDir, parts[1].trim()).absolutePath
                                    symlinks.add(Pair(oldPath, newPath))
                                    
                                    // 确保父目录存在
                                    File(newPath).parentFile?.mkdirs()
                                } else {
                                    log(paths, "Warning: Malformed symlink line: $line")
                                }
                                line = reader.readLine()
                            }
                        }
                        log(paths, "Found ${symlinks.size} symlinks")
                    } else {
                        // 处理普通文件和目录
                        val targetFile = File(paths.stagingPrefixDir, zipEntryName)
                        val isDirectory = zipEntry.isDirectory
                        
                        if (isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            
                            FileOutputStream(targetFile).use { outStream ->
                                var readBytes: Int
                                while (zipInput.read(buffer).also { readBytes = it } != -1) {
                                    outStream.write(buffer, 0, readBytes)
                                }
                            }
                            
                            // 设置可执行权限（按照官方规则）
                            if (zipEntryName.startsWith("bin/") ||
                                zipEntryName.startsWith("libexec") ||
                                zipEntryName.startsWith("lib/apt/apt-helper") ||
                                zipEntryName.startsWith("lib/apt/methods")) {
                                try {
                                    Os.chmod(targetFile.absolutePath, 448) // 0700 = 448
                                } catch (e: Exception) {
                                    // 如果 Os.chmod 失败，使用 Java 方法
                                    targetFile.setExecutable(true, false)
                                    targetFile.setReadable(true, false)
                                    targetFile.setWritable(true, true)
                                }
                            }
                        }
                        
                        fileCount++
                        if (fileCount % 100 == 0) {
                            val progress = 0.2f + (fileCount * 0.0005f).coerceAtMost(0.5f)
                            onProgress(progress, "解压中... ($fileCount 个文件)")
                        }
                    }
                    
                    zipEntry = zipInput.nextEntry
                }
            }
            
            log(paths, "Total files extracted: $fileCount")
            
            // Step 5: 创建符号链接
            if (symlinks.isEmpty()) {
                throw RuntimeException("No SYMLINKS.txt found in bootstrap")
            }
            
            onProgress(0.75f, "正在创建符号链接...")
            log(paths, "Creating ${symlinks.size} symlinks...")
            
            for ((oldPath, newPath) in symlinks) {
                try {
                    Os.symlink(oldPath, newPath)
                } catch (e: Exception) {
                    log(paths, "Warning: Failed to create symlink: $newPath -> $oldPath: ${e.message}")
                    // 继续处理其他符号链接
                }
            }
            
            // Step 6: 原子性地移动到最终目录
            onProgress(0.9f, "正在完成安装...")
            log(paths, "Moving staging to prefix directory...")
            
            // 确保 prefix 目录不存在（即使之前删除过，也再次确认）
            // 这样 renameTo 一定会成功
            if (paths.prefixDir.exists()) {
                log(paths, "Warning: prefix directory exists before rename, deleting it...")
                deleteDirectory(paths.prefixDir)
            }
            
            if (!paths.stagingPrefixDir.renameTo(paths.prefixDir)) {
                throw RuntimeException("Failed to rename staging directory to prefix directory")
            }
            
            onProgress(1.0f, "安装完成！")
            log(paths, "=== Termux Bootstrap Install Complete ===")
            
        } catch (e: Exception) {
            log(paths, "Install failed: ${e.message}")
            log(paths, "Stack trace: ${e.stackTraceToString()}")
            
            // 清理失败的安装
            try {
                deleteDirectory(paths.stagingPrefixDir)
                deleteDirectory(paths.prefixDir)
            } catch (cleanupError: Exception) {
                log(paths, "Cleanup failed: ${cleanupError.message}")
            }
            
            throw e
        }
    }
    
    /**
     * 删除目录（递归）
     */
    private fun deleteDirectory(dir: File) {
        if (dir.exists()) {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    deleteDirectory(file)
                }
            }
            dir.delete()
        }
    }
    
    /**
     * 写入日志
     */
    private fun log(paths: TermuxPaths, message: String) {
        try {
            val logFile = File(paths.logDir, "bootstrap_install.log")
            val timestamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                java.util.Locale.US
            ).format(java.util.Date())
            logFile.appendText("[$timestamp] $message\n")
        } catch (_: Exception) {
            // 忽略日志错误
        }
    }
}
