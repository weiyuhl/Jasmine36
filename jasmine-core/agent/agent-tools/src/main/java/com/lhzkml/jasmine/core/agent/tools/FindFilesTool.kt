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
 * 文件查找工具（对标 Cursor Glob）
 *
 * 功能：
 * - 支持完整路径 glob 匹配（e.g. "** / *.kt", "src/main/** / *.xml"）
 * - 不以 "**/" 开头的模式自动补全为递归搜索
 * - 支持按修改时间排序（默认）或按名称排序
 * - 支持模糊子串匹配
 * - 返回文件大小信息
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class FindFilesTool(
    private val basePath: String? = null
) : Tool() {

    companion object {
        private val SKIP_DIRS = setOf(
            ".git", ".svn", ".hg", "node_modules", "build", ".gradle",
            ".idea", "__pycache__", ".DS_Store", "dist", ".next"
        )
    }

    override val descriptor = ToolDescriptor(
        name = "find_files",
        description = "Searches for files matching a glob pattern or substring. " +
            "Works fast with codebases of any size. " +
            "Returns matching file paths sorted by modification time (most recent first) by default. " +
            "Patterns not starting with '**/' are automatically prepended with '**/' to enable recursive searching. " +
            "Examples: '*.kt' (becomes '**/*.kt') finds all .kt files; " +
            "'**/test/**/*.ts' finds all .ts files in test directories; " +
            "'build.gradle' finds all files named build.gradle. " +
            "Path can be relative (resolved against workspace root) or absolute. Use '.' for workspace root.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Starting directory to search from (use '.' for workspace root)", ToolParameterType.StringType),
            ToolParameterDescriptor("pattern", "File name/path pattern: glob (e.g. '*.kt', '**/*.xml') or substring to match", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("limit", "Maximum number of results to return. Default 30", ToolParameterType.IntegerType),
            ToolParameterDescriptor("sort_by", "Sort results by 'modified' (most recent first, default) or 'name' (alphabetical)",
                ToolParameterType.EnumType(listOf("modified", "name"))),
            ToolParameterDescriptor("include_hidden", "Include hidden files/directories (starting with '.'). Default false",
                ToolParameterType.BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val pattern = obj["pattern"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'pattern'"
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 30
        val sortBy = obj["sort_by"]?.jsonPrimitive?.content ?: "modified"
        val includeHidden = obj["include_hidden"]?.jsonPrimitive?.boolean ?: false

        val dir = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!dir.exists()) return "Error: Path does not exist: $path"
        if (!dir.isDirectory) return "Error: Not a directory: $path"

        val isGlob = pattern.contains('*') || pattern.contains('?') || pattern.contains('[')

        val globRegex = if (isGlob) {
            val normalizedPattern = if (!pattern.startsWith("**/") && !pattern.startsWith("/")) {
                "**/$pattern"
            } else {
                pattern
            }
            globToRegex(normalizedPattern)
        } else null
        val lowerPattern = pattern.lowercase(java.util.Locale.getDefault())

        return try {
            val results = mutableListOf<File>()
            searchRecursive(dir, dir, globRegex, lowerPattern, includeHidden, results)

            if (results.isEmpty()) {
                "No files found matching: $pattern"
            } else {
                val sorted = when (sortBy) {
                    "modified" -> results.sortedByDescending { it.lastModified() }
                    else -> results.sortedBy { it.name.lowercase() }
                }
                val limited = sorted.take(limit)

                buildString {
                    appendLine("Found ${results.size} file(s)${if (results.size > limit) " (showing top $limit)" else ""}:")
                    for (file in limited) {
                        val rel = if (basePath != null) {
                            file.relativeTo(File(basePath)).path.replace('\\', '/')
                        } else {
                            file.relativeTo(dir).path.replace('\\', '/')
                        }
                        val size = formatSize(file.length())
                        appendLine("  $rel ($size)")
                    }
                    if (results.size > limit) {
                        appendLine("  ... (${results.size - limit} more results not shown)")
                    }
                }.trimEnd()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun searchRecursive(
        root: File, current: File, globRegex: Regex?, lowerPattern: String,
        includeHidden: Boolean, results: MutableList<File>
    ) {
        val children = current.listFiles()?.sortedBy { it.name } ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (!includeHidden && child.name.startsWith(".")) continue
                if (child.name in SKIP_DIRS) continue
                searchRecursive(root, child, globRegex, lowerPattern, includeHidden, results)
            } else {
                val matches = if (globRegex != null) {
                    val relPath = child.relativeTo(root).path.replace('\\', '/')
                    globRegex.matches(relPath) || globRegex.matches(child.name)
                } else {
                    child.name.lowercase(java.util.Locale.getDefault()).contains(lowerPattern)
                }
                if (matches) {
                    results.add(child)
                }
            }
        }
    }

    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            else -> "${size / (1024 * 1024)}MB"
        }
    }

    private fun globToRegex(glob: String): Regex {
        val regex = buildString {
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
                    '[' -> append("[")
                    ']' -> append("]")
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
