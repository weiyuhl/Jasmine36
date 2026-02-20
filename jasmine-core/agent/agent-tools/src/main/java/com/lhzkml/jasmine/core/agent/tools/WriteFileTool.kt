package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 参考 koog 的 WriteFileTool + AetherLink 的 write_to_file 增强
 * 写入文本内容到文件，自动创建父目录，覆盖已有内容
 * 增强功能：行数验证（防截断）、代码省略检测、自动备份
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class WriteFileTool(
    private val basePath: String? = null
) : Tool() {

    companion object {
        /** 代码省略标记模式 */
        private val OMISSION_PATTERNS = listOf(
            "// rest of code unchanged",
            "// ... rest",
            "// remaining code",
            "/* ... */",
            "// ...",
            "# rest of code",
            "# ...",
            "// 其余代码不变",
            "// 省略"
        )
    }

    override val descriptor = ToolDescriptor(
        name = "write_file",
        description = "Writes text content to a file. Creates parent directories if needed and overwrites existing content. " +
            "Provide line_count to enable truncation detection. " +
            "Set create_backup to true to auto-backup before overwriting. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the target file (relative to workspace root, or absolute)", ToolParameterType.StringType),
            ToolParameterDescriptor("content", "Text content to write (must not be empty). Overwrites existing content completely", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("line_count", "Expected line count for truncation detection", ToolParameterType.IntegerType),
            ToolParameterDescriptor("create_backup", "Auto-backup existing file before overwriting. Default false", ToolParameterType.BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val content = obj["content"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'content'"
        val lineCount = obj["line_count"]?.jsonPrimitive?.int
        val createBackup = obj["create_backup"]?.jsonPrimitive?.boolean ?: false

        if (content.isEmpty()) return "Error: Content must not be empty"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"

        // 行数验证 - 防止内容截断
        val actualLineCount = content.lines().size
        if (lineCount != null && lineCount > 0) {
            if (actualLineCount < lineCount * 0.8) {
                val omissionDetected = detectCodeOmission(content)
                if (omissionDetected) {
                    return "Error: Content may be truncated (actual $actualLineCount lines, expected $lineCount lines). " +
                        "Code omission markers detected. Please provide complete content or use edit_file for incremental changes."
                }
            }
        }

        return try {
            // 自动备份
            var backupInfo = ""
            if (createBackup && file.exists()) {
                val backupFile = File(file.parent, "${file.name}.backup.${System.currentTimeMillis()}")
                file.copyTo(backupFile, overwrite = true)
                backupInfo = " (backup: ${backupFile.name})"
            }

            file.parentFile?.mkdirs()
            file.writeText(content)

            "Written: ${file.name} ($actualLineCount lines, ${content.length} chars)$backupInfo"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * 检测代码省略标记
     */
    private fun detectCodeOmission(content: String): Boolean {
        val lowerContent = content.lowercase()
        return OMISSION_PATTERNS.any { lowerContent.contains(it.lowercase()) }
    }

    private fun resolveFile(path: String): File? {
        val file = if (basePath != null && !File(path).isAbsolute) {
            File(basePath, path)
        } else {
            File(path)
        }
        if (basePath != null) {
            val base = File(basePath).canonicalFile
            val resolved = file.canonicalFile
            if (!resolved.path.startsWith(base.path)) return null
        }
        return file
    }
}
