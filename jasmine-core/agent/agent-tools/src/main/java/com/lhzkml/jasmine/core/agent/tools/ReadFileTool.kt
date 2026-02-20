package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 参考 koog 的 ReadFileTool + AetherLink 的 read_file 增强
 * 读取文本文件内容，支持行范围选择（0-based, endLine exclusive, -1 表示读到末尾）
 * 增强功能：批量读取多个文件、Token 预算控制、代码定义提取
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class ReadFileTool(
    private val basePath: String? = null
) : Tool() {

    companion object {
        /** 每个 Token 大约对应的字符数 */
        private const val CHARS_PER_TOKEN = 4
        /** 默认最大 Token 预算 */
        private const val DEFAULT_MAX_TOKENS = 100_000
    }

    override val descriptor = ToolDescriptor(
        name = "read_file",
        description = "Reads a text file with optional line range selection. Returns file content with line numbers. TEXT-ONLY. " +
            "Supports batch reading multiple files (use 'files' array). " +
            "Supports token budget control (use 'context_tokens') and code definition extraction (use 'extract_definitions'). " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = emptyList(),
        optionalParameters = listOf(
            ToolParameterDescriptor("path", "Path to a single text file (use this OR 'files', not both)", StringType),
            ToolParameterDescriptor("files", "Batch read: array of objects with path, startLine, endLine",
                ListType(ObjectType(
                    properties = listOf(
                        ToolParameterDescriptor("path", "File path", StringType),
                        ToolParameterDescriptor("startLine", "Start line (0-based)", IntegerType),
                        ToolParameterDescriptor("endLine", "End line (0-based, exclusive, -1=end)", IntegerType)
                    ),
                    requiredProperties = listOf("path")
                ))
            ),
            ToolParameterDescriptor("startLine", "First line to include (0-based, inclusive). Default 0", IntegerType),
            ToolParameterDescriptor("endLine", "First line to exclude (0-based, exclusive). -1 means read to end. Default -1", IntegerType),
            ToolParameterDescriptor("extract_definitions", "Extract code definitions (functions, classes). Default false", BooleanType),
            ToolParameterDescriptor("context_tokens", "Already used context tokens, for budget control. Default 0", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject

        // 批量读取模式
        val filesArray = obj["files"]?.jsonArray
        if (filesArray != null && filesArray.isNotEmpty()) {
            val contextTokens = obj["context_tokens"]?.jsonPrimitive?.int ?: 0
            val extractDefs = obj["extract_definitions"]?.jsonPrimitive?.boolean ?: false
            return readMultipleFiles(filesArray, contextTokens, extractDefs)
        }

        // 单文件读取模式
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path' or 'files'"
        val startLine = obj["startLine"]?.jsonPrimitive?.int ?: 0
        val endLine = obj["endLine"]?.jsonPrimitive?.int ?: -1
        val extractDefs = obj["extract_definitions"]?.jsonPrimitive?.boolean ?: false
        val contextTokens = obj["context_tokens"]?.jsonPrimitive?.int ?: 0

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: File not found: $path"
        if (!file.isFile) return "Error: Not a file: $path"

        return try {
            val lines = file.readLines()
            val end = if (endLine < 0) lines.size else endLine.coerceAtMost(lines.size)
            val start = startLine.coerceIn(0, end)
            val selected = lines.subList(start, end)

            // Token 预算控制
            val maxChars = ((DEFAULT_MAX_TOKENS - contextTokens) * CHARS_PER_TOKEN).coerceAtLeast(1000)
            var truncated = false

            buildString {
                appendLine("File: ${file.name} (${lines.size} lines)")
                if (start > 0 || end < lines.size) {
                    appendLine("Lines: $start-$end of ${lines.size}")
                }
                appendLine("---")

                var charCount = 0
                for (i in selected.indices) {
                    val line = "${start + i}: ${selected[i]}"
                    charCount += line.length + 1
                    if (charCount > maxChars) {
                        appendLine("... [truncated: token budget exceeded, use line range to read remaining]")
                        truncated = true
                        break
                    }
                    appendLine(line)
                }

                if (truncated) {
                    appendLine("Truncated at token budget. Total lines: ${lines.size}")
                }

                // 代码定义提取
                if (extractDefs) {
                    val defs = extractCodeDefinitions(lines, file.name)
                    if (defs.isNotEmpty()) {
                        appendLine("---")
                        appendLine("Code definitions:")
                        defs.forEach { appendLine("  $it") }
                    }
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun readMultipleFiles(
        filesArray: kotlinx.serialization.json.JsonArray,
        contextTokens: Int,
        extractDefs: Boolean
    ): String {
        var usedTokens = contextTokens
        val maxTotalChars = (DEFAULT_MAX_TOKENS * CHARS_PER_TOKEN)
        val results = mutableListOf<String>()
        var successCount = 0
        var errorCount = 0

        for (element in filesArray) {
            val fileObj = element.jsonObject
            val path = fileObj["path"]?.jsonPrimitive?.content ?: continue
            val startLine = fileObj["startLine"]?.jsonPrimitive?.int ?: 0
            val endLine = fileObj["endLine"]?.jsonPrimitive?.int ?: -1

            val file = resolveFile(path)
            if (file == null) {
                results.add("[$path] Error: Path not allowed")
                errorCount++
                continue
            }
            if (!file.exists()) {
                results.add("[$path] Error: File not found")
                errorCount++
                continue
            }
            if (!file.isFile) {
                results.add("[$path] Error: Not a file")
                errorCount++
                continue
            }

            try {
                val lines = file.readLines()
                val end = if (endLine < 0) lines.size else endLine.coerceAtMost(lines.size)
                val start = startLine.coerceIn(0, end)
                val selected = lines.subList(start, end)

                val remainingChars = (maxTotalChars - usedTokens * CHARS_PER_TOKEN).coerceAtLeast(1000)
                var truncated = false

                val content = buildString {
                    appendLine("=== ${file.name} (${lines.size} lines) ===")
                    if (start > 0 || end < lines.size) {
                        appendLine("Lines: $start-$end of ${lines.size}")
                    }
                    var charCount = 0
                    for (i in selected.indices) {
                        val line = "${start + i}: ${selected[i]}"
                        charCount += line.length + 1
                        if (charCount > remainingChars) {
                            appendLine("... [truncated: token budget]")
                            truncated = true
                            break
                        }
                        appendLine(line)
                    }
                    if (extractDefs) {
                        val defs = extractCodeDefinitions(lines, file.name)
                        if (defs.isNotEmpty()) {
                            appendLine("Definitions:")
                            defs.forEach { appendLine("  $it") }
                        }
                    }
                }.trimEnd()

                usedTokens += content.length / CHARS_PER_TOKEN
                results.add(content)
                successCount++
            } catch (e: Exception) {
                results.add("[$path] Error: ${e.message}")
                errorCount++
            }
        }

        return buildString {
            appendLine("Batch read: $successCount success, $errorCount error")
            appendLine("---")
            results.forEach { appendLine(it) }
        }.trimEnd()
    }

    /**
     * 提取代码定义（函数、类、接口等）
     * 简单的基于正则的提取，支持常见语言
     */
    private fun extractCodeDefinitions(lines: List<String>, fileName: String): List<String> {
        val defs = mutableListOf<String>()
        val ext = fileName.substringAfterLast('.', "").lowercase()

        val patterns = when (ext) {
            "kt", "kts" -> listOf(
                Regex("""^\s*((?:public|private|protected|internal|abstract|open|data|sealed|enum|annotation)\s+)*(class|interface|object|fun)\s+\w+"""),
                Regex("""^\s*val\s+\w+\s*:"""),
                Regex("""^\s*var\s+\w+\s*:""")
            )
            "java" -> listOf(
                Regex("""^\s*((?:public|private|protected|static|abstract|final)\s+)*(class|interface|enum)\s+\w+"""),
                Regex("""^\s*((?:public|private|protected|static|abstract|final|synchronized)\s+)+\w+\s+\w+\s*\(""")
            )
            "ts", "tsx", "js", "jsx" -> listOf(
                Regex("""^\s*(export\s+)?(default\s+)?(async\s+)?function\s+\w+"""),
                Regex("""^\s*(export\s+)?(default\s+)?(abstract\s+)?class\s+\w+"""),
                Regex("""^\s*(export\s+)?(default\s+)?interface\s+\w+"""),
                Regex("""^\s*(export\s+)?const\s+\w+\s*=""")
            )
            "py" -> listOf(
                Regex("""^\s*(async\s+)?def\s+\w+"""),
                Regex("""^\s*class\s+\w+""")
            )
            else -> return emptyList()
        }

        for ((lineNum, line) in lines.withIndex()) {
            for (pattern in patterns) {
                val match = pattern.find(line)
                if (match != null) {
                    defs.add("L${lineNum}: ${match.value.trim()}")
                    break
                }
            }
        }
        return defs
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
