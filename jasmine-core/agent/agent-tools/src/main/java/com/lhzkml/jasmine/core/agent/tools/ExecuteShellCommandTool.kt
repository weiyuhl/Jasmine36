package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /**
     * 判断命令是否需要确认
     * @return true 表示需要弹确认框，false 表示自动执行
     */
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
 * 参考 koog 的 ExecuteShellCommandTool
 * 执行 shell 命令，带策略化确认机制和超时控制
 *
 * @param confirmationHandler 确认回调，返回 true 允许执行，false 拒绝
 * @param policyConfig Shell 命令执行策略配置，控制哪些命令需要确认
 * @param basePath 工作目录限制（安全沙箱），null 表示不限制
 */
class ExecuteShellCommandTool(
    private val confirmationHandler: suspend (command: String, workingDirectory: String?) -> Boolean = { _, _ -> true },
    private val policyConfig: ShellPolicyConfig = ShellPolicyConfig(),
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "execute_shell_command",
        description = "Executes a shell command with optional working directory and timeout. " +
            "Returns command output and exit code. Each call runs in a new isolated shell, " +
            "so directory changes (cd) do NOT persist. Use workingDirectory parameter instead.",
        requiredParameters = listOf(
            ToolParameterDescriptor("command", "The shell command to execute (e.g. 'git status', 'ls -la')", ToolParameterType.StringType),
            ToolParameterDescriptor("timeoutSeconds", "Maximum execution time in seconds. Commands exceeding this are terminated", ToolParameterType.IntegerType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("workingDirectory", "Absolute path where the command runs. Default: current directory", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val command = obj["command"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'command'"
        val timeoutSeconds = obj["timeoutSeconds"]?.jsonPrimitive?.int ?: 60
        val workingDirectory = obj["workingDirectory"]?.jsonPrimitive?.content

        // 根据策略判断是否需要确认
        if (policyConfig.needsConfirmation(command)) {
            val approved = confirmationHandler(command, workingDirectory)
            if (!approved) {
                return "Command execution denied: $command"
            }
        }

        // 验证工作目录
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
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val processBuilder = if (isWindows) {
                ProcessBuilder("cmd", "/c", command)
            } else {
                ProcessBuilder("sh", "-c", command)
            }

            processBuilder.redirectErrorStream(true)
            workDir?.let { processBuilder.directory(it) }

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                buildString {
                    appendLine("Command: $command")
                    if (output.isNotEmpty()) appendLine(output)
                    appendLine("Command timed out after ${timeoutSeconds}s")
                }.trimEnd()
            } else {
                buildString {
                    appendLine("Command: $command")
                    if (output.isNotEmpty()) appendLine(output) else appendLine("(no output)")
                    appendLine("Exit code: ${process.exitValue()}")
                }.trimEnd()
            }
        } catch (e: Exception) {
            "Error: Failed to execute command: ${e.message}"
        }
    }
}
