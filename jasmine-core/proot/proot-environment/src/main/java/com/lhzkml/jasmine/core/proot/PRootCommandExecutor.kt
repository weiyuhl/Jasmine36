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

        // Ensure PRoot binary has execute permission
        if (!paths.prootBinary.canExecute()) {
            paths.prootBinary.setExecutable(true, false)
            if (!paths.prootBinary.canExecute()) {
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", paths.prootBinary.absolutePath)).waitFor()
                } catch (_: Exception) {}
            }
            if (!paths.prootBinary.canExecute()) {
                return@withContext PRootResult(
                    output = "Error: PRoot binary is not executable. Path: ${paths.prootBinary.absolutePath}, exists=${paths.prootBinary.exists()}, size=${paths.prootBinary.length()}",
                    exitCode = -1
                )
            }
        }

        val args = buildCommandArgs(paths, command, workingDirectory, extraBindPaths)

        val processBuilder = ProcessBuilder(args)
        processBuilder.redirectErrorStream(true)

        val env = processBuilder.environment()
        env["HOME"] = "/root"
        env["PROOT_TMP_DIR"] = paths.baseDir.absolutePath
        env["PROOT_NO_SECCOMP"] = "1"
        if (paths.prootLoader.exists()) {
            env["PROOT_LOADER"] = paths.prootLoader.absolutePath
        }
        if (paths.prootLoader32.exists()) {
            env["PROOT_LOADER_32"] = paths.prootLoader32.absolutePath
        }
        val ldPaths = mutableListOf<String>()
        val libSearchDir = paths.libSearchDir
        if (libSearchDir.exists()) ldPaths.add(libSearchDir.absolutePath)
        if (paths.nativeLibDir?.exists() == true) ldPaths.add(paths.nativeLibDir.absolutePath)
        if (ldPaths.isNotEmpty()) {
            env["LD_LIBRARY_PATH"] = ldPaths.joinToString(":")
        }
        env.remove("LD_PRELOAD")

        for ((key, value) in environment) {
            env[key] = value
        }

        val process = try {
            processBuilder.start()
        } catch (e: Exception) {
            return@withContext PRootResult(
                output = "Error: Failed to start PRoot process: ${e.message}",
                exitCode = -1
            )
        }

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
        args.add("--root-id")
        args.add("-w")
        args.add(workingDirectory)
        args.add("--link2symlink")
        args.add("--kill-on-exit")
        args.add("--sysvipc")
        args.add("-L")
        args.add("--kernel-release=6.17.0-PRoot-Distro")

        // Core filesystem binds (matching Termux proot-distro)
        args.add("-b"); args.add("/dev")
        args.add("-b"); args.add("/dev/urandom:/dev/random")
        args.add("-b"); args.add("/proc")
        args.add("-b"); args.add("/proc/self/fd:/dev/fd")
        args.add("-b"); args.add("/sys")

        // Fake /proc entries (matching Termux proot-distro setup_fake_sysdata)
        val fakeProc = File(paths.baseDir, "fake_proc")
        val fakeProcBinds = mapOf(
            ".loadavg" to "/proc/loadavg",
            ".stat" to "/proc/stat",
            ".uptime" to "/proc/uptime",
            ".version" to "/proc/version",
            ".vmstat" to "/proc/vmstat",
            ".sysctl_entry_cap_last_cap" to "/proc/sys/kernel/cap_last_cap",
            ".sysctl_inotify_max_user_watches" to "/proc/sys/fs/inotify/max_user_watches"
        )
        for ((file, target) in fakeProcBinds) {
            val f = File(fakeProc, file)
            if (f.exists()) {
                args.add("-b"); args.add("${f.absolutePath}:$target")
            }
        }

        // Hide SELinux
        val emptyDir = File(paths.baseDir, "fake_sys_empty")
        if (emptyDir.exists()) {
            args.add("-b"); args.add("${emptyDir.absolutePath}:/sys/fs/selinux")
        }

        for ((hostPath, guestPath) in extraBindPaths) {
            val host = File(hostPath)
            if (host.exists()) {
                args.add("-b")
                args.add("$hostPath:$guestPath")
            }
        }

        // Use /usr/bin/env -i to set a clean environment (like Termux proot-distro)
        args.add("/usr/bin/env")
        args.add("-i")
        args.add("HOME=/root")
        args.add("LANG=C.UTF-8")
        args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        args.add("TERM=xterm-256color")
        args.add("TMPDIR=/tmp")
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
