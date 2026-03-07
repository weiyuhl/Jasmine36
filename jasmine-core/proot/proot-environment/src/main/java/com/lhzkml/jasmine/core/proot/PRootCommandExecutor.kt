package com.lhzkml.jasmine.core.proot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 通过 PRoot 在 Alpine rootfs 中执行命令。
 *
 * 原理：将用户命令包装为 PRoot 进程调用，
 * 通过 ptrace 系统调用实现用户态 chroot，无需 root。
 */
object PRootCommandExecutor {

    suspend fun execute(
        paths: PRootPaths,
        command: String,
        workingDirectory: String = "/root",
        extraBindPaths: List<Pair<String, String>> = emptyList(),
        environment: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 60,
        background: Boolean = false
    ): PRootResult = withContext(Dispatchers.IO) {
        if (!AlpineBootstrap.isInstalled(paths)) {
            return@withContext PRootResult(
                output = "Error: PRoot environment not installed. Please install Alpine Linux first.",
                exitCode = -1
            )
        }

        val args = buildCommandArgs(paths, command, workingDirectory, extraBindPaths)

        val processBuilder = ProcessBuilder(args)
        processBuilder.redirectErrorStream(true)

        val env = processBuilder.environment()
        env["HOME"] = "/root"
        env["PROOT_TMP_DIR"] = paths.baseDir.absolutePath
        env["PROOT_NO_SECCOMP"] = "1"
        env.remove("LD_PRELOAD")

        for ((key, value) in environment) {
            env[key] = value
        }

        val process = processBuilder.start()

        if (background) {
            Thread.sleep(1000)
            val partial = readAvailableOutput(process)
            return@withContext PRootResult(
                output = buildString {
                    appendLine("Command running in background in PRoot/Alpine environment")
                    appendLine("Command: $command")
                    if (partial.isNotEmpty()) appendLine(partial)
                },
                exitCode = 0
            )
        }

        val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)

        if (!finished) {
            val partial = readAvailableOutput(process)
            PRootResult(
                output = buildString {
                    appendLine("Command timed out after ${timeoutSeconds}s (still running in background)")
                    appendLine("Partial output:")
                    appendLine(partial.ifEmpty { "(no output yet)" })
                }.trimEnd(),
                exitCode = -1,
                timedOut = true
            )
        } else {
            val output = process.inputStream.bufferedReader().readText()
            PRootResult(
                output = output,
                exitCode = process.exitValue()
            )
        }
    }

    private fun buildCommandArgs(
        paths: PRootPaths,
        command: String,
        workingDirectory: String,
        extraBindPaths: List<Pair<String, String>>
    ): List<String> {
        val args = mutableListOf<String>()

        args.add(paths.prootBinary.absolutePath)
        args.add("--rootfs=${paths.rootfsDir.absolutePath}")
        args.add("-0") // fake root
        args.add("-w")
        args.add(workingDirectory)

        for (bindPath in AlpineConstants.DEFAULT_BIND_PATHS) {
            args.add("-b")
            args.add(bindPath)
        }

        for ((hostPath, guestPath) in extraBindPaths) {
            val host = File(hostPath)
            if (host.exists()) {
                args.add("-b")
                args.add("$hostPath:$guestPath")
            }
        }

        args.add("/bin/sh")
        args.add("-c")
        args.add(command)

        return args
    }

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
}
