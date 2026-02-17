package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 参考 koog 的 ExecuteShellCommandTool
 * 执行 shell 命令，带确认机制和超时控制
 *
 * @param confirmationHandler 确认回调，返回 true 允许执行，false 拒绝。默认自动允许（brave mode）
 * @param basePath 工作目录限制（安全沙箱），null 表示不限制
 */
class ExecuteShellCommandTool(
    private val confirmationHandler: suspend (command: String, workingDirectory: String?) -> Boolean = { _, _ -> true },
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

        // 确认机制
        val approved = confirmationHandler(command, workingDirectory)
        if (!approved) {
            return "Command execution denied: $command"
        }

        // 验证工作目录
        val workDir = if (workingDirectory != null) {
            val dir = File(workingDirectory)
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
