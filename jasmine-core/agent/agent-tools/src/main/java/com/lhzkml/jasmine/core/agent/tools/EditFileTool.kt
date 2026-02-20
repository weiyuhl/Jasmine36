package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 参考 koog 的 EditFileTool + AetherLink 的 apply_diff 增强
 * 通过查找替换编辑文件，支持模糊空白匹配
 * original 为空字符串时表示创建新文件或完全重写
 *
 * 增强功能：
 * - SEARCH/REPLACE 块格式支持（自动检测）
 * - 可配置模糊匹配阈值
 * - 按文件路径的重试计数
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class EditFileTool(
    private val basePath: String? = null
) : Tool() {

    companion object {
        private const val DEFAULT_FUZZY_THRESHOLD = 0.9f
        /** 按文件路径的重试计数 */
        private val retryCount = mutableMapOf<String, Int>()
    }

    override val descriptor = ToolDescriptor(
        name = "edit_file",
        description = "Makes an edit to a target file by applying a single text replacement. " +
            "Searches for 'original' text and replaces it with 'replacement'. " +
            "Use empty string for 'original' when creating new files or performing complete rewrites. " +
            "Also supports SEARCH/REPLACE block format in 'original' parameter: " +
            "<<<<<<< SEARCH\\n[search text]\\n=======\\n[replace text]\\n>>>>>>> REPLACE " +
            "(when using this format, 'replacement' parameter is ignored). " +
            "Only ONE replacement per call. Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the target file (relative to workspace root, or absolute)", ToolParameterType.StringType),
            ToolParameterDescriptor("original", "The exact text block to find and replace. Use empty string for new files or full rewrites. " +
                "Can also be SEARCH/REPLACE block format.", ToolParameterType.StringType),
            ToolParameterDescriptor("replacement", "The new text content that will replace the original text block", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("fuzzy_threshold", "Fuzzy match threshold (0.0-1.0). Default 0.9. Higher = stricter", ToolParameterType.FloatType)
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
        val fuzzyThreshold = obj["fuzzy_threshold"]?.jsonPrimitive?.float ?: DEFAULT_FUZZY_THRESHOLD

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"

        return try {
            if (original.isEmpty()) {
                // 创建新文件或完全重写
                file.parentFile?.mkdirs()
                file.writeText(replacement)
                retryCount.remove(path)
                return "Created/rewritten: ${file.name}"
            }

            if (!file.exists()) {
                incrementRetry(path)
                return retryHint(path, "File not found: $path")
            }

            val content = file.readText()

            // 检测 SEARCH/REPLACE 块格式
            if (isSearchReplaceFormat(original)) {
                return applySearchReplaceBlocks(file, content, original, path, fuzzyThreshold)
            }

            // 先尝试精确匹配
            if (content.contains(original)) {
                val newContent = content.replaceFirst(original, replacement)
                file.writeText(newContent)
                retryCount.remove(path)
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
                    retryCount.remove(path)
                    return "Successfully edited (fuzzy match): ${file.name}"
                }
            }

            // 基于相似度的模糊匹配
            val fuzzyResult = fuzzyReplaceWithThreshold(content, original, replacement, fuzzyThreshold)
            if (fuzzyResult != null) {
                file.writeText(fuzzyResult)
                retryCount.remove(path)
                return "Successfully edited (similarity match): ${file.name}"
            }

            incrementRetry(path)
            retryHint(path, "Original text not found in file: $path")
        } catch (e: Exception) {
            incrementRetry(path)
            "Error: ${e.message}"
        }
    }

    /**
     * 检测是否为 SEARCH/REPLACE 块格式
     */
    private fun isSearchReplaceFormat(text: String): Boolean {
        return text.contains("<<<<<<< SEARCH") && text.contains(">>>>>>> REPLACE")
    }

    /**
     * 解析并应用 SEARCH/REPLACE 块
     */
    private fun applySearchReplaceBlocks(
        file: File, content: String, blocksText: String, path: String, threshold: Float
    ): String {
        val blocks = parseSearchReplaceBlocks(blocksText)
        if (blocks.isEmpty()) {
            incrementRetry(path)
            return retryHint(path, "Invalid SEARCH/REPLACE format - no blocks found")
        }

        var currentContent = content
        var appliedCount = 0
        val failures = mutableListOf<String>()

        for ((index, block) in blocks.withIndex()) {
            // 精确匹配
            if (currentContent.contains(block.search)) {
                currentContent = currentContent.replaceFirst(block.search, block.replace)
                appliedCount++
                continue
            }
            // 模糊匹配
            val fuzzyResult = fuzzyReplace(currentContent, block.search, block.replace)
            if (fuzzyResult != null) {
                currentContent = fuzzyResult
                appliedCount++
                continue
            }
            // 相似度匹配
            val simResult = fuzzyReplaceWithThreshold(currentContent, block.search, block.replace, threshold)
            if (simResult != null) {
                currentContent = simResult
                appliedCount++
                continue
            }
            failures.add("Block ${index + 1}: search text not found")
        }

        if (appliedCount == 0) {
            incrementRetry(path)
            return retryHint(path, "All SEARCH/REPLACE blocks failed: ${failures.joinToString("; ")}")
        }

        file.writeText(currentContent)
        retryCount.remove(path)

        return if (failures.isEmpty()) {
            "Applied ${appliedCount}/${blocks.size} SEARCH/REPLACE block(s) to ${file.name}"
        } else {
            "Partial success: applied $appliedCount/${blocks.size} block(s) to ${file.name}. Failures: ${failures.joinToString("; ")}"
        }
    }

    /**
     * 解析 SEARCH/REPLACE 块
     */
    private data class SearchReplaceBlock(val search: String, val replace: String)

    private fun parseSearchReplaceBlocks(text: String): List<SearchReplaceBlock> {
        val blocks = mutableListOf<SearchReplaceBlock>()
        val pattern = Regex(
            """<<<<<<< SEARCH\s*\n(.*?)\n=======\n(.*?)\n>>>>>>> REPLACE""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in pattern.findAll(text)) {
            val search = match.groupValues[1]
                .lines()
                .dropWhile { it.matches(Regex(""":start_line:\d+""")) }
                .joinToString("\n")
                .let { if (it.startsWith("-------\n")) it.removePrefix("-------\n") else it }
            val replace = match.groupValues[2]
            blocks.add(SearchReplaceBlock(search, replace))
        }
        return blocks
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

    /**
     * 基于行相似度的模糊替换
     */
    private fun fuzzyReplaceWithThreshold(
        content: String, original: String, replacement: String, threshold: Float
    ): String? {
        val contentLines = content.lines()
        val originalLines = original.lines()
        if (originalLines.isEmpty() || contentLines.size < originalLines.size) return null

        var bestScore = 0f
        var bestIndex = -1

        for (i in 0..contentLines.size - originalLines.size) {
            var matchCount = 0
            for (j in originalLines.indices) {
                val sim = lineSimilarity(contentLines[i + j].trim(), originalLines[j].trim())
                if (sim >= threshold) matchCount++
            }
            val score = matchCount.toFloat() / originalLines.size
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }

        if (bestScore >= threshold && bestIndex >= 0) {
            val before = contentLines.subList(0, bestIndex)
            val after = contentLines.subList(bestIndex + originalLines.size, contentLines.size)
            return (before + replacement.lines() + after).joinToString("\n")
        }
        return null
    }

    /**
     * 简单的行相似度计算（基于最长公共子序列）
     */
    private fun lineSimilarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val maxLen = maxOf(a.length, b.length)
        val lcs = lcsLength(a, b)
        return lcs.toFloat() / maxLen
    }

    private fun lcsLength(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1)
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1
                else maxOf(prev[j], curr[j - 1])
            }
            val tmp = prev; prev = curr; curr = tmp
            curr.fill(0)
        }
        return prev[n]
    }

    private fun incrementRetry(path: String) {
        retryCount[path] = (retryCount[path] ?: 0) + 1
    }

    private fun retryHint(path: String, error: String): String {
        val count = retryCount[path] ?: 0
        val hint = if (count >= 3) {
            " Suggestion: This file has failed $count times. Use read_file to get the latest content and try again."
        } else ""
        return "Error: $error (attempt $count)$hint"
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
