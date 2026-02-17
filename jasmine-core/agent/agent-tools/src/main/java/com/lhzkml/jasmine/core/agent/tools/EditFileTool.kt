package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 参考 koog 的 EditFileTool
 * 通过查找替换编辑文件，支持模糊空白匹配
 * original 为空字符串时表示创建新文件或完全重写
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class EditFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "edit_file",
        description = "Makes an edit to a target file by applying a single text replacement. " +
            "Searches for 'original' text and replaces it with 'replacement'. " +
            "Use empty string for 'original' when creating new files or performing complete rewrites. " +
            "Only ONE replacement per call.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Absolute path to the target file to modify or create", ToolParameterType.StringType),
            ToolParameterDescriptor("original", "The exact text block to find and replace. Use empty string for new files or full rewrites", ToolParameterType.StringType),
            ToolParameterDescriptor("replacement", "The new text content that will replace the original text block", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val original = obj["original"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'original'"
        val replacement = obj["replacement"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'replacement'"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"

        return try {
            if (original.isEmpty()) {
                // 创建新文件或完全重写
                file.parentFile?.mkdirs()
                file.writeText(replacement)
                return "Created/rewritten: ${file.name}"
            }

            if (!file.exists()) return "Error: File not found: $path"

            val content = file.readText()

            // 先尝试精确匹配
            if (content.contains(original)) {
                val newContent = content.replaceFirst(original, replacement)
                file.writeText(newContent)
                return "Successfully edited: ${file.name}"
            }

            // 模糊空白匹配（参考 koog 的 token normalized patch）
            val normalizedContent = normalizeWhitespace(content)
            val normalizedOriginal = normalizeWhitespace(original)

            if (normalizedContent.contains(normalizedOriginal)) {
                // 找到模糊匹配位置，在原始内容中定位并替换
                val newContent = fuzzyReplace(content, original, replacement)
                if (newContent != null) {
                    file.writeText(newContent)
                    return "Successfully edited (fuzzy match): ${file.name}"
                }
            }

            "Error: Original text not found in file: $path"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun normalizeWhitespace(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun fuzzyReplace(content: String, original: String, replacement: String): String? {
        val contentLines = content.lines()
        val originalLines = original.lines().map { it.trim() }

        // 滑动窗口查找匹配的行块
        for (i in 0..contentLines.size - originalLines.size) {
            var match = true
            for (j in originalLines.indices) {
                if (contentLines[i + j].trim() != originalLines[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val before = contentLines.subList(0, i)
                val after = contentLines.subList(i + originalLines.size, contentLines.size)
                return (before + replacement.lines() + after).joinToString("\n")
            }
        }
        return null
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
