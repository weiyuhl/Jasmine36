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
 * 参考 koog 的 RegexSearchTool
 * 在文件或目录中执行正则表达式搜索，返回匹配的文件路径、行号和上下文片段
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class RegexSearchTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "search_by_regex",
        description = "Executes a regular expression search on file or directory contents. " +
            "Returns file paths, line numbers, and excerpts where the pattern was found. Read-only. " +
            "Path can be relative (resolved against workspace root) or absolute. Use '.' for workspace root.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Starting directory or file path (relative to workspace root, or absolute)", ToolParameterType.StringType),
            ToolParameterDescriptor("regex", "Regular expression pattern to search for", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("limit", "Maximum number of matching files to return. Default 25", ToolParameterType.IntegerType),
            ToolParameterDescriptor("caseSensitive", "If true, case-sensitive matching. Default false", ToolParameterType.BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val pattern = obj["regex"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'regex'"
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 25
        val caseSensitive = obj["caseSensitive"]?.jsonPrimitive?.boolean ?: false

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: Path does not exist: $path"

        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val regex = try {
            Regex(pattern, options)
        } catch (e: Exception) {
            return "Error: Invalid regex: ${e.message}"
        }

        return try {
            val results = mutableListOf<String>()
            searchFiles(file, regex, limit, results)
            if (results.isEmpty()) "No matches found."
            else results.joinToString("\n\n")
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun searchFiles(file: File, regex: Regex, limit: Int, results: MutableList<String>) {
        if (results.size >= limit) return

        if (file.isFile) {
            searchInFile(file, regex, results, limit)
        } else if (file.isDirectory) {
            val children = file.listFiles()?.sortedBy { it.name } ?: return
            for (child in children) {
                if (results.size >= limit) return
                searchFiles(child, regex, limit, results)
            }
        }
    }

    private fun searchInFile(file: File, regex: Regex, results: MutableList<String>, limit: Int) {
        if (results.size >= limit) return
        // 跳过二进制文件（简单启发式）
        if (isBinaryFile(file)) return

        try {
            val lines = file.readLines()
            val matches = mutableListOf<String>()

            for ((lineNum, line) in lines.withIndex()) {
                if (regex.containsMatchIn(line)) {
                    // 上下文：前后各 2 行
                    val contextStart = (lineNum - 2).coerceAtLeast(0)
                    val contextEnd = (lineNum + 3).coerceAtMost(lines.size)
                    val snippet = lines.subList(contextStart, contextEnd)
                        .mapIndexed { i, l ->
                            val num = contextStart + i
                            val marker = if (num == lineNum) ">" else " "
                            "$marker${num}: $l"
                        }.joinToString("\n")
                    matches.add(snippet)
                }
            }

            if (matches.isNotEmpty()) {
                val entry = buildString {
                    appendLine("File: ${file.path}")
                    appendLine("Matches: ${matches.size}")
                    matches.take(5).forEach { appendLine(it) }
                    if (matches.size > 5) appendLine("... and ${matches.size - 5} more matches")
                }
                results.add(entry.trimEnd())
            }
        } catch (_: Exception) {
            // 忽略无法读取的文件
        }
    }

    private fun isBinaryFile(file: File): Boolean {
        if (file.length() > 10 * 1024 * 1024) return true // 跳过 >10MB
        val binaryExtensions = setOf("jar", "class", "so", "dll", "exe", "png", "jpg", "gif", "zip", "gz", "tar", "pdf")
        return file.extension.lowercase() in binaryExtensions
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
