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
 * 参考 koog 的 ReadFileTool
 * 读取文本文件内容，支持行范围选择（0-based, endLine exclusive, -1 表示读到末尾）
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class ReadFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "read_file",
        description = "Reads a text file with optional line range selection. Returns file content with line numbers. TEXT-ONLY.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Absolute path to the text file to read", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("startLine", "First line to include (0-based, inclusive). Default 0", ToolParameterType.IntegerType),
            ToolParameterDescriptor("endLine", "First line to exclude (0-based, exclusive). -1 means read to end. Default -1", ToolParameterType.IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val startLine = obj["startLine"]?.jsonPrimitive?.int ?: 0
        val endLine = obj["endLine"]?.jsonPrimitive?.int ?: -1

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: File not found: $path"
        if (!file.isFile) return "Error: Not a file: $path"

        return try {
            val lines = file.readLines()
            val end = if (endLine < 0) lines.size else endLine.coerceAtMost(lines.size)
            val start = startLine.coerceIn(0, end)
            val selected = lines.subList(start, end)

            buildString {
                appendLine("File: ${file.name} (${lines.size} lines)")
                if (start > 0 || end < lines.size) {
                    appendLine("Lines: $start-$end of ${lines.size}")
                }
                appendLine("---")
                selected.forEachIndexed { i, line ->
                    appendLine("${start + i}: $line")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun resolveFile(path: String): File? {
        val file = File(path)
        if (basePath != null) {
            val base = File(basePath).canonicalFile
            val resolved = file.canonicalFile
            if (!resolved.path.startsWith(base.path)) return null
        }
        return file
    }
}
