package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 在文件指定行插入内容工具
 * 移植自 AetherLink 的 insert_content
 * 内容插入到指定行之前，行号从 1 开始
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class InsertContentTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "insert_content",
        description = "Inserts content at a specific line in a file. Content is inserted before the specified line. " +
            "Line numbers are 1-based. Use line 1 to insert at the beginning of the file. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the target file", ToolParameterType.StringType),
            ToolParameterDescriptor("line", "Line number (1-based) to insert before", ToolParameterType.IntegerType),
            ToolParameterDescriptor("content", "Content to insert", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val line = obj["line"]?.jsonPrimitive?.int
            ?: return "Error: Missing parameter 'line'"
        val content = obj["content"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'content'"

        if (line < 1) return "Error: line must be a positive integer (1-based)"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: File not found: $path"
        if (!file.isFile) return "Error: Not a file: $path"

        return try {
            val lines = file.readLines().toMutableList()
            val insertLines = content.lines()
            val insertAt = (line - 1).coerceAtMost(lines.size)

            lines.addAll(insertAt, insertLines)
            file.writeText(lines.joinToString("\n"))

            "Inserted ${insertLines.size} line(s) at line $line in ${file.name} (total: ${lines.size} lines)"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
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
