package com.lhzkml.jasmine.core.termux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Termux 命令执行结果
 */
data class TermuxResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false
)

/**
 * Termux 命令执行器
 * 
 * 在 Termux 环境中执行 Shell 命令。
 * 参考 Termux 的命令执行机制。
 */
object TermuxCommandExecutor {
    
    /**
     * 执行命令
     * 
     * @param paths Termux 路径配置
     * @param command 要执行的命令
     * @param workingDirectory 工作目录（可选）
     * @param environment 额外的环境变量
     * @param timeoutSeconds 超时时间（秒）
     * @param background 是否后台运行
     */
    suspend fun execute(
        paths: TermuxPaths,
        command: String,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 60,
        background: Boolean = false
    ): TermuxResult = withContext(Dispatchers.IO) {
        
        // 构建环境变量
        val env = buildEnvironment(paths, environment)
        
        // 构建命令
        val shell = File(paths.prefixDir, "bin/bash").absolutePath
        val fullCommand = arrayOf(shell, "-c", command)
        
        // 创建进程
        val processBuilder = ProcessBuilder(*fullCommand)
        processBuilder.redirectErrorStream(true)
        
        // 设置工作目录
        val workDir = if (workingDirectory != null) {
            val dir = File(workingDirectory)
            if (dir.exists() && dir.isDirectory) dir else null
        } else {
            paths.homeDir
        }
        workDir?.let { processBuilder.directory(it) }

        
        // 设置环境变量
        processBuilder.environment().clear()
        processBuilder.environment().putAll(env)
        
        // 启动进程
        val process = processBuilder.start()
        
        // 后台运行模式
        if (background) {
            Thread.sleep(1000) // 等待 1 秒
            val partialOutput = readAvailableOutput(process)
            val pid = getProcessId(process)
            return@withContext TermuxResult(
                exitCode = 0,
                output = "Running in background (pid: $pid)\n$partialOutput",
                timedOut = false
            )
        }
        
        // 等待执行完成
        val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        
        if (!finished) {
            // 超时
            val partialOutput = readAvailableOutput(process)
            process.destroy()
            TermuxResult(
                exitCode = -1,
                output = "Command timed out after ${timeoutSeconds}s\n$partialOutput",
                timedOut = true
            )
        } else {
            // 正常完成
            val output = process.inputStream.bufferedReader().readText()
            TermuxResult(
                exitCode = process.exitValue(),
                output = output,
                timedOut = false
            )
        }
    }
    
    /**
     * 构建环境变量
     */
    private fun buildEnvironment(
        paths: TermuxPaths,
        extra: Map<String, String>
    ): Map<String, String> {
        val prefix = paths.prefixDir.absolutePath
        return mapOf(
            "HOME" to paths.homeDir.absolutePath,
            "PREFIX" to prefix,
            "PATH" to "$prefix/bin:$prefix/bin/applets",
            "LD_LIBRARY_PATH" to "$prefix/lib",
            "TMPDIR" to paths.tmpDir.absolutePath,
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor"
        ) + extra
    }
    
    /**
     * 读取可用的输出
     */
    private fun readAvailableOutput(process: Process): String {
        return try {
            val stream = process.inputStream
            val available = stream.available()
            if (available > 0) {
                val buffer = ByteArray(available.coerceAtMost(64 * 1024))
                val read = stream.read(buffer)
                if (read > 0) String(buffer, 0, read) else ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }
    
    /**
     * 获取进程 ID
     */
    private fun getProcessId(process: Process): String {
        return try {
            val pidMethod = process.javaClass.getMethod("pid")
            pidMethod.invoke(process)?.toString() ?: process.hashCode().toString()
        } catch (_: Exception) {
            try {
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.get(process)?.toString() ?: process.hashCode().toString()
            } catch (_: Exception) {
                process.hashCode().toString()
            }
        }
    }
}
