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
 * 强大的正则搜索工具（对标 Cursor Grep / ripgrep）
 *
 * 功能：
 * - 三种输出模式：content（匹配行+上下文）、files_with_matches（仅文件路径）、count（匹配计数）
 * - 可配置上下文行数（-A, -B, -C）
 * - glob 文件过滤和 type 文件类型过滤
 * - head_limit 限制结果数量 + offset 分页
 * - multiline 跨行匹配
 * - 大小写不敏感选项
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class RegexSearchTool(
    private val basePath: String? = null
) : Tool() {

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024
        private val BINARY_EXTENSIONS = setOf(
            "jar", "class", "so", "dll", "exe", "png", "jpg", "jpeg",
            "gif", "webp", "zip", "gz", "tar", "pdf", "ico", "bmp",
            "mp3", "mp4", "avi", "mov", "ttf", "otf", "woff", "woff2",
            "apk", "aab", "dex", "o", "a", "lib"
        )
        private val SKIP_DIRS = setOf(
            ".git", ".svn", ".hg", "node_modules", "build", ".gradle",
            ".idea", "__pycache__", ".DS_Store", "dist", ".next"
        )

        private val TYPE_EXTENSIONS = mapOf(
            "kt" to setOf("kt", "kts"),
            "java" to setOf("java"),
            "js" to setOf("js", "mjs", "cjs"),
            "ts" to setOf("ts", "tsx"),
            "py" to setOf("py", "pyi"),
            "rust" to setOf("rs"),
            "go" to setOf("go"),
            "c" to setOf("c", "h"),
            "cpp" to setOf("cpp", "cxx", "cc", "hpp", "hxx", "hh"),
            "swift" to setOf("swift"),
            "ruby" to setOf("rb"),
            "php" to setOf("php"),
            "xml" to setOf("xml"),
            "json" to setOf("json"),
            "yaml" to setOf("yaml", "yml"),
            "toml" to setOf("toml"),
            "md" to setOf("md", "markdown"),
            "html" to setOf("html", "htm"),
            "css" to setOf("css", "scss", "sass", "less"),
            "sql" to setOf("sql"),
            "sh" to setOf("sh", "bash", "zsh"),
            "gradle" to setOf("gradle", "gradle.kts")
        )
    }

    override val descriptor = ToolDescriptor(
        name = "search_by_regex",
        description = "A powerful search tool built on regular expressions. " +
            "Supports full regex syntax. " +
            "Filter files with glob parameter (e.g. '*.kt', '**/*.tsx') or type parameter (e.g. 'kt', 'py', 'js'). " +
            "Output modes: 'content' shows matching lines with context (default), " +
            "'files_with_matches' shows only file paths, 'count' shows match counts per file. " +
            "Use context_before/context_after to control context lines shown around matches. " +
            "Multiline matching: set multiline=true for patterns that span across lines. " +
            "Results are capped by head_limit; use offset for pagination. " +
            "Path can be relative (resolved against workspace root) or absolute. Use '.' for workspace root.",
        requiredParameters = listOf(
            ToolParameterDescriptor("pattern", "The regular expression pattern to search for in file contents", ToolParameterType.StringType),
            ToolParameterDescriptor("path", "File or directory to search in. Use '.' for workspace root", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("output_mode",
                "Output mode: 'content' shows matching lines (default), 'files_with_matches' shows file paths, 'count' shows match counts",
                ToolParameterType.EnumType(listOf("content", "files_with_matches", "count"))),
            ToolParameterDescriptor("context_before",
                "Number of lines to show before each match (like rg -B). Default 0. Only for content mode",
                ToolParameterType.IntegerType),
            ToolParameterDescriptor("context_after",
                "Number of lines to show after each match (like rg -A). Default 0. Only for content mode",
                ToolParameterType.IntegerType),
            ToolParameterDescriptor("context",
                "Number of lines to show before AND after each match (like rg -C). Overrides context_before/context_after if set",
                ToolParameterType.IntegerType),
            ToolParameterDescriptor("case_insensitive",
                "Case insensitive search (like rg -i). Default false",
                ToolParameterType.BooleanType),
            ToolParameterDescriptor("glob",
                "Glob pattern to filter files (e.g. '*.kt', '*.{ts,tsx}')",
                ToolParameterType.StringType),
            ToolParameterDescriptor("type",
                "File type to search (e.g. 'kt', 'py', 'js', 'java', 'ts', 'go', 'rust'). More efficient than glob for standard types",
                ToolParameterType.StringType),
            ToolParameterDescriptor("head_limit",
                "Limit output size. For content mode: limits total matches shown. For files/count modes: limits number of files. Default 50",
                ToolParameterType.IntegerType),
            ToolParameterDescriptor("offset",
                "Skip first N entries for pagination. Default 0",
                ToolParameterType.IntegerType),
            ToolParameterDescriptor("multiline",
                "Enable multiline mode where . matches newlines and patterns can span lines. Default false",
                ToolParameterType.BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val pattern = obj["pattern"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'pattern'"
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"

        val outputMode = obj["output_mode"]?.jsonPrimitive?.content ?: "content"
        val contextC = obj["context"]?.jsonPrimitive?.int
        val contextBefore = contextC ?: (obj["context_before"]?.jsonPrimitive?.int ?: 0)
        val contextAfter = contextC ?: (obj["context_after"]?.jsonPrimitive?.int ?: 0)
        val caseInsensitive = obj["case_insensitive"]?.jsonPrimitive?.boolean ?: false
        val globPattern = obj["glob"]?.jsonPrimitive?.content
        val fileType = obj["type"]?.jsonPrimitive?.content
        val headLimit = obj["head_limit"]?.jsonPrimitive?.int ?: DEFAULT_LIMIT
        val offset = obj["offset"]?.jsonPrimitive?.int ?: 0
        val multiline = obj["multiline"]?.jsonPrimitive?.boolean ?: false

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: Path does not exist: $path"

        val regexOptions = mutableSetOf<RegexOption>()
        if (caseInsensitive) regexOptions.add(RegexOption.IGNORE_CASE)
        if (multiline) {
            regexOptions.add(RegexOption.MULTILINE)
            regexOptions.add(RegexOption.DOT_MATCHES_ALL)
        }

        val regex = try {
            Regex(pattern, regexOptions)
        } catch (e: Exception) {
            return "Error: Invalid regex: ${e.message}"
        }

        val typeExtensions = fileType?.let { TYPE_EXTENSIONS[it.lowercase()] }
        val globRegex = globPattern?.let { buildGlobRegex(it) }

        return try {
            when (outputMode) {
                "files_with_matches" -> searchFilesOnly(file, regex, globRegex, typeExtensions, headLimit, offset, multiline)
                "count" -> searchCount(file, regex, globRegex, typeExtensions, headLimit, offset, multiline)
                else -> searchContent(file, regex, globRegex, typeExtensions, contextBefore, contextAfter, headLimit, offset, multiline)
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ==================== Content mode ====================

    private fun searchContent(
        root: File, regex: Regex, globRegex: Regex?, typeExts: Set<String>?,
        ctxBefore: Int, ctxAfter: Int, limit: Int, offset: Int, multiline: Boolean
    ): String {
        val allMatches = mutableListOf<MatchEntry>()
        collectMatches(root, root, regex, globRegex, typeExts, allMatches, multiline)

        val total = allMatches.size
        val paginated = allMatches.drop(offset).take(limit)

        if (paginated.isEmpty()) {
            return if (total == 0) "No matches found."
            else "No matches in range (total: $total, offset: $offset, limit: $limit)"
        }

        return buildString {
            for (entry in paginated) {
                val relPath = relativePath(root, entry.file)
                appendLine(relPath)
                for (match in entry.matches) {
                    val lines = entry.lines
                    val startCtx = (match.lineNum - ctxBefore).coerceAtLeast(0)
                    val endCtx = (match.lineNum + ctxAfter + 1).coerceAtMost(lines.size)

                    for (i in startCtx until endCtx) {
                        val prefix = if (i == match.lineNum) ":" else "-"
                        appendLine("${i + 1}$prefix${lines[i]}")
                    }
                    if (ctxBefore > 0 || ctxAfter > 0) appendLine("--")
                }
                appendLine()
            }
            if (total > offset + limit) {
                appendLine("... showing ${paginated.size} of at least $total matches (offset=$offset, limit=$limit)")
            }
        }.trimEnd()
    }

    // ==================== Files-only mode ====================

    private fun searchFilesOnly(
        root: File, regex: Regex, globRegex: Regex?, typeExts: Set<String>?,
        limit: Int, offset: Int, multiline: Boolean
    ): String {
        val matchingFiles = mutableListOf<File>()
        collectMatchingFiles(root, root, regex, globRegex, typeExts, matchingFiles, multiline)

        val total = matchingFiles.size
        val paginated = matchingFiles.drop(offset).take(limit)

        if (paginated.isEmpty()) {
            return if (total == 0) "No matching files found."
            else "No files in range (total: $total, offset: $offset, limit: $limit)"
        }

        return buildString {
            paginated.forEach { f ->
                appendLine(relativePath(root, f))
            }
            if (total > offset + limit) {
                appendLine("... $total total files (showing ${paginated.size})")
            }
        }.trimEnd()
    }

    // ==================== Count mode ====================

    private fun searchCount(
        root: File, regex: Regex, globRegex: Regex?, typeExts: Set<String>?,
        limit: Int, offset: Int, multiline: Boolean
    ): String {
        val counts = mutableListOf<Pair<File, Int>>()
        collectCounts(root, root, regex, globRegex, typeExts, counts, multiline)

        val total = counts.size
        val paginated = counts.drop(offset).take(limit)

        if (paginated.isEmpty()) {
            return if (total == 0) "No matches found."
            else "No files in range (total: $total, offset: $offset, limit: $limit)"
        }

        return buildString {
            paginated.forEach { (file, count) ->
                appendLine("${relativePath(root, file)}:$count")
            }
            if (total > offset + limit) {
                appendLine("... $total total files (showing ${paginated.size})")
            }
        }.trimEnd()
    }

    // ==================== Collectors ====================

    private data class LineMatch(val lineNum: Int, val line: String)
    private data class MatchEntry(val file: File, val lines: List<String>, val matches: List<LineMatch>)

    private fun collectMatches(
        root: File, current: File, regex: Regex, globRegex: Regex?,
        typeExts: Set<String>?, results: MutableList<MatchEntry>, multiline: Boolean
    ) {
        if (current.isFile) {
            if (!shouldSearchFile(current, root, globRegex, typeExts)) return
            findMatchesInFile(current, regex, multiline)?.let { results.add(it) }
        } else if (current.isDirectory) {
            val children = current.listFiles()?.sortedBy { it.name } ?: return
            for (child in children) {
                if (child.isDirectory && child.name in SKIP_DIRS) continue
                collectMatches(root, child, regex, globRegex, typeExts, results, multiline)
            }
        }
    }

    private fun collectMatchingFiles(
        root: File, current: File, regex: Regex, globRegex: Regex?,
        typeExts: Set<String>?, results: MutableList<File>, multiline: Boolean
    ) {
        if (current.isFile) {
            if (!shouldSearchFile(current, root, globRegex, typeExts)) return
            if (fileContainsMatch(current, regex, multiline)) results.add(current)
        } else if (current.isDirectory) {
            val children = current.listFiles()?.sortedBy { it.name } ?: return
            for (child in children) {
                if (child.isDirectory && child.name in SKIP_DIRS) continue
                collectMatchingFiles(root, child, regex, globRegex, typeExts, results, multiline)
            }
        }
    }

    private fun collectCounts(
        root: File, current: File, regex: Regex, globRegex: Regex?,
        typeExts: Set<String>?, results: MutableList<Pair<File, Int>>, multiline: Boolean
    ) {
        if (current.isFile) {
            if (!shouldSearchFile(current, root, globRegex, typeExts)) return
            val count = countMatchesInFile(current, regex, multiline)
            if (count > 0) results.add(current to count)
        } else if (current.isDirectory) {
            val children = current.listFiles()?.sortedBy { it.name } ?: return
            for (child in children) {
                if (child.isDirectory && child.name in SKIP_DIRS) continue
                collectCounts(root, child, regex, globRegex, typeExts, results, multiline)
            }
        }
    }

    // ==================== File-level search ====================

    private fun findMatchesInFile(file: File, regex: Regex, multiline: Boolean): MatchEntry? {
        if (isBinaryFile(file)) return null
        return try {
            if (multiline) {
                val content = file.readText()
                val matchResults = regex.findAll(content).toList()
                if (matchResults.isEmpty()) return null
                val lines = content.lines()
                val lineMatches = mutableListOf<LineMatch>()
                for (m in matchResults) {
                    val lineNum = content.substring(0, m.range.first).count { it == '\n' }
                    lineMatches.add(LineMatch(lineNum, lines.getOrElse(lineNum) { "" }))
                }
                MatchEntry(file, lines, lineMatches)
            } else {
                val lines = file.readLines()
                val lineMatches = mutableListOf<LineMatch>()
                for ((idx, line) in lines.withIndex()) {
                    if (regex.containsMatchIn(line)) {
                        lineMatches.add(LineMatch(idx, line))
                    }
                }
                if (lineMatches.isEmpty()) null
                else MatchEntry(file, lines, lineMatches)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fileContainsMatch(file: File, regex: Regex, multiline: Boolean): Boolean {
        if (isBinaryFile(file)) return false
        return try {
            if (multiline) {
                regex.containsMatchIn(file.readText())
            } else {
                file.useLines { lines -> lines.any { regex.containsMatchIn(it) } }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun countMatchesInFile(file: File, regex: Regex, multiline: Boolean): Int {
        if (isBinaryFile(file)) return 0
        return try {
            if (multiline) {
                regex.findAll(file.readText()).count()
            } else {
                file.useLines { lines -> lines.count { regex.containsMatchIn(it) } }
            }
        } catch (_: Exception) {
            0
        }
    }

    // ==================== Filtering ====================

    private fun shouldSearchFile(file: File, root: File, globRegex: Regex?, typeExts: Set<String>?): Boolean {
        if (isBinaryFile(file)) return false
        if (typeExts != null && file.extension.lowercase() !in typeExts) return false
        if (globRegex != null) {
            val relPath = file.relativeTo(root).path.replace('\\', '/')
            val name = file.name
            if (!globRegex.matches(relPath) && !globRegex.matches(name)) return false
        }
        return true
    }

    private fun isBinaryFile(file: File): Boolean {
        if (file.length() > MAX_FILE_SIZE) return true
        return file.extension.lowercase() in BINARY_EXTENSIONS
    }

    // ==================== Helpers ====================

    private fun relativePath(root: File, file: File): String {
        return try {
            if (basePath != null) {
                file.relativeTo(File(basePath)).path.replace('\\', '/')
            } else if (root.isDirectory) {
                file.relativeTo(root).path.replace('\\', '/')
            } else {
                file.absolutePath
            }
        } catch (_: Exception) {
            file.absolutePath
        }
    }

    private fun buildGlobRegex(glob: String): Regex {
        val regex = buildString {
            append("^")
            var i = 0
            while (i < glob.length) {
                when (glob[i]) {
                    '*' -> {
                        if (i + 1 < glob.length && glob[i + 1] == '*') {
                            append(".*")
                            i += 2
                            if (i < glob.length && glob[i] == '/') i++
                            continue
                        } else {
                            append("[^/]*")
                        }
                    }
                    '?' -> append("[^/]")
                    '.' -> append("\\.")
                    '{' -> append("(?:")
                    '}' -> append(")")
                    ',' -> append("|")
                    '(' -> append("\\(")
                    ')' -> append("\\)")
                    '+' -> append("\\+")
                    '^' -> append("\\^")
                    '$' -> append("\\$")
                    '/' -> append("/")
                    else -> append(glob[i])
                }
                i++
            }
            append("$")
        }
        return Regex(regex, RegexOption.IGNORE_CASE)
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
