package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.lhzkml.jasmine.core.proot.PRootEnvironment
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shell 命令执行策略
 * - MANUAL: 所有命令都需手动确认
 * - BLACKLIST: 黑名单内的命令需确认，其余自动执行
 * - WHITELIST: 白名单内的命令自动执行，其余需确认
 */
enum class ShellPolicy {
    MANUAL, BLACKLIST, WHITELIST
}

/**
 * Shell 命令执行策略配置
 * @param policy 执行策略类型
 * @param blacklist 黑名单关键词列表（命令包含关键词则需确认）
 * @param whitelist 白名单关键词列表（命令以关键词开头则自动执行）
 */
data class ShellPolicyConfig(
    val policy: ShellPolicy = ShellPolicy.MANUAL,
    val blacklist: List<String> = DEFAULT_BLACKLIST,
    val whitelist: List<String> = DEFAULT_WHITELIST
) {
    companion object {
        val DEFAULT_BLACKLIST = listOf(
            "rm ", "rm -", "rmdir", "del ", "format", "mkfs",
            "dd ", "shutdown", "reboot", "> /dev/", "chmod 777"
        )
        val DEFAULT_WHITELIST = listOf(
            "ls", "pwd", "cat ", "echo ", "git ", "find ",
            "grep ", "head ", "tail ", "wc ", "which ", "whoami"
        )
    }

    fun needsConfirmation(command: String): Boolean {
        val cmdLower = command.lowercase(Locale.getDefault())
        return when (policy) {
            ShellPolicy.MANUAL -> true
            ShellPolicy.BLACKLIST -> {
                blacklist.any { keyword ->
                    cmdLower.contains(keyword.lowercase(Locale.getDefault()))
                }
            }
            ShellPolicy.WHITELIST -> {
                !whitelist.any { keyword ->
                    cmdLower.startsWith(keyword.lowercase(Locale.getDefault()))
                }
            }
        }
    }
}

/**
 * Shell 命令执行工具（对标 Cursor Shell 工具）
 *
 * 功能增强：
 * - 后台执行支持（block_until_ms）：超时后不杀进程，返回已收集的输出并标记为后台运行
 * - purpose 参数：要求说明执行命令的目的
 * - description 参数：命令的简短描述（5-10 词）
 * - workingDirectory 参数：指定工作目录
 * - 安全策略配置（MANUAL/BLACKLIST/WHITELIST）
 *
 * @param confirmationHandler 确认回调，返回 true 允许执行，false 拒绝
 * @param policyConfig Shell 命令执行策略配置，控制哪些命令需要确认
 * @param basePath 工作目录限制（安全沙箱），null 表示不限制
 */
class ExecuteShellCommandTool(
    private val confirmationHandler: suspend (command: String, purpose: String, workingDirectory: String?) -> Boolean = { _, _, _ -> true },
    private val policyConfig: ShellPolicyConfig = ShellPolicyConfig(),
    private val basePath: String? = null,
    private val prootEnvironment: PRootEnvironment? = null
) : Tool() {

    private val hasLinuxEnv = prootEnvironment?.isInstalled == true

    override val descriptor = ToolDescriptor(
        name = "execute_shell_command",
        description = "Executes a shell command with optional working directory and timeout. " +
            "Returns command output and exit code. Each call runs in a new isolated shell, " +
            "so directory changes (cd) do NOT persist. Use workingDirectory parameter instead. " +
            "You MUST provide a clear 'purpose' explaining WHY you are running this command." +
            if (hasLinuxEnv) " Set usePRoot=true to run in a full Alpine Linux environment " +
                "with apk package manager, python, gcc, git, curl, etc." else "",
        requiredParameters = listOf(
            ToolParameterDescriptor("command", "The shell command to execute (e.g. 'git status', 'ls -la')", ToolParameterType.StringType),
            ToolParameterDescriptor("purpose", "A brief explanation of why this command is being executed and what you expect to achieve (e.g. 'Check current git branch to determine deployment target')", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("description", "Clear, concise description of what this command does in 5-10 words", ToolParameterType.StringType),
            ToolParameterDescriptor("timeoutSeconds", "Maximum time to wait for completion before returning partial output. Default 60. The command continues running in background if it exceeds this", ToolParameterType.IntegerType),
            ToolParameterDescriptor("workingDirectory", "Absolute path where the command runs. Default: workspace root", ToolParameterType.StringType),
            ToolParameterDescriptor("background", "If true, immediately returns without waiting for completion (for dev servers, watchers). Default false", ToolParameterType.BooleanType),
            ToolParameterDescriptor("usePRoot", "If true, runs the command inside the PRoot/Alpine Linux environment with full package manager and tools. Default false", ToolParameterType.BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val command = obj["command"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'command'"
        val purpose = obj["purpose"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'purpose'. You must explain why you are running this command."
        val description = obj["description"]?.jsonPrimitive?.content
        val timeoutSeconds = obj["timeoutSeconds"]?.jsonPrimitive?.int ?: 60
        val workingDirectory = obj["workingDirectory"]?.jsonPrimitive?.content
        val background = obj["background"]?.jsonPrimitive?.boolean ?: false
        val usePRoot = obj["usePRoot"]?.jsonPrimitive?.boolean ?: false

        if (policyConfig.needsConfirmation(command)) {
            val approved = confirmationHandler(command, purpose, workingDirectory)
            if (!approved) {
                return "Command execution denied: $command\nPurpose: $purpose"
            }
        }

        if (usePRoot) {
            return executePRoot(command, purpose, description, timeoutSeconds, workingDirectory, background)
        }

        val workDir = if (workingDirectory != null) {
            val dir = if (basePath != null && !File(workingDirectory).isAbsolute) {
                File(basePath, workingDirectory)
            } else {
                File(workingDirectory)
            }
            if (basePath != null) {
                val base = File(basePath).canonicalFile
                if (!dir.canonicalFile.path.startsWith(base.path)) {
                    return "Error: Working directory not allowed: $workingDirectory"
                }
            }
            if (!dir.exists() || !dir.isDirectory) {
                return "Error: Working directory does not exist: $workingDirectory"
            }
            dir
        } else if (basePath != null) {
            val dir = File(basePath)
            if (dir.exists() && dir.isDirectory) dir else null
        } else null

        return try {
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            val processBuilder = if (isWindows) {
                ProcessBuilder("cmd", "/c", command)
            } else {
                ProcessBuilder("sh", "-c", command)
            }

            processBuilder.redirectErrorStream(true)
            workDir?.let { processBuilder.directory(it) }

            val process = processBuilder.start()

            if (background) {
                val partialOutput = readPartialOutput(process, 1)
                val pid = getProcessId(process)
                return buildString {
                    description?.let { appendLine("Description: $it") }
                    appendLine("Purpose: $purpose")
                    appendLine("Command: $command")
                    appendLine("Status: Running in background (pid: $pid)")
                    if (partialOutput.isNotEmpty()) appendLine(partialOutput)
                }.trimEnd()
            }

            val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)

            if (!finished) {
                val partialOutput = readAvailableOutput(process)
                val pid = getProcessId(process)
                buildString {
                    appendLine("Purpose: $purpose")
                    appendLine("Command: $command")
                    appendLine("Status: Moved to background after ${timeoutSeconds}s (pid: $pid)")
                    appendLine("The command is still running. Partial output collected so far:")
                    if (partialOutput.isNotEmpty()) appendLine(partialOutput) else appendLine("(no output yet)")
                }.trimEnd()
            } else {
                val output = process.inputStream.bufferedReader().readText()
                buildString {
                    appendLine("Purpose: $purpose")
                    appendLine("Command: $command")
                    if (output.isNotEmpty()) appendLine(output) else appendLine("(no output)")
                    appendLine("Exit code: ${process.exitValue()}")
                }.trimEnd()
            }
        } catch (e: Exception) {
            "Error: Failed to execute command: ${e.message}"
        }
    }

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

    private fun readPartialOutput(process: Process, waitSeconds: Long): String {
        return try {
            Thread.sleep(waitSeconds * 1000)
            readAvailableOutput(process)
        } catch (_: Exception) {
            ""
        }
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

    private suspend fun executePRoot(
        command: String,
        purpose: String,
        description: String?,
        timeoutSeconds: Int,
        workingDirectory: String?,
        background: Boolean
    ): String {
        val env = prootEnvironment
            ?: return "Error: PRoot/Alpine Linux environment is not configured. Please install it in Settings > Linux Environment."

        if (!env.isInstalled) {
            return "Error: PRoot/Alpine Linux environment is not installed. Please install it in Settings > Linux Environment."
        }

        val extraBinds = if (basePath != null) {
            listOf(basePath to "/workspace")
        } else {
            emptyList()
        }

        val prootWorkDir = workingDirectory ?: if (basePath != null) "/workspace" else "/root"

        return try {
            val result = env.executeCommand(
                command = command,
                workingDirectory = prootWorkDir,
                extraBindPaths = extraBinds,
                timeoutSeconds = timeoutSeconds,
                background = background
            )
            buildString {
                appendLine("Purpose: $purpose")
                description?.let { appendLine("Description: $it") }
                appendLine("Environment: PRoot/Alpine Linux")
                appendLine("Command: $command")
                if (result.output.isNotEmpty()) appendLine(result.output)
                if (!result.timedOut) appendLine("Exit code: ${result.exitCode}")
            }.trimEnd()
        } catch (e: Exception) {
            "Error: PRoot command failed: ${e.message}"
        }
    }
}
