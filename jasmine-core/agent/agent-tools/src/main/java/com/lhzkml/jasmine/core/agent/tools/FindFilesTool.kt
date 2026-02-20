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
 * 按文件名搜索工具
 * 在工作区中递归搜索匹配文件名的文件，支持模糊匹配和 glob 模式
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class FindFilesTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "find_files",
        description = "Searches for files by name in the workspace. Supports glob patterns (e.g. '*.kt', '**/*.xml') " +
            "and fuzzy substring matching. Returns matching file paths with size info. " +
            "Path can be relative (resolved against workspace root) or absolute. Use '.' for workspace root.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Starting directory to search from (use '.' for workspace root)", ToolParameterType.StringType),
            ToolParameterDescriptor("pattern", "File name pattern: glob (e.g. '*.kt') or substring to match against file names", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("limit", "Maximum number of results to return. Default 30", ToolParameterType.IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val pattern = obj["pattern"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'pattern'"
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 30

        val dir = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!dir.exists()) return "Error: Path does not exist: $path"
        if (!dir.isDirectory) return "Error: Not a directory: $path"

        val isGlob = pattern.contains('*') || pattern.contains('?') || pattern.contains('[')
        val globRegex = if (isGlob) globToRegex(pattern) else null
        val lowerPattern = pattern.lowercase(java.util.Locale.getDefault())

        return try {
            val results = mutableListOf<File>()
            searchRecursive(dir, globRegex, lowerPattern, limit, results)

            if (results.isEmpty()) {
                "No files found matching: $pattern"
            } else {
                buildString {
                    appendLine("Found ${results.size} file(s):")
                    for (file in results) {
                        val rel = if (basePath != null) {
                            file.relativeTo(File(basePath)).path.replace('\\', '/')
                        } else {
                            file.absolutePath
                        }
                        val size = formatSize(file.length())
                        appendLine("  $rel ($size)")
                    }
                    if (results.size >= limit) {
                        appendLine("  ... (limited to $limit results)")
                    }
                }.trimEnd()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun searchRecursive(dir: File, globRegex: Regex?, lowerPattern: String, limit: Int, results: MutableList<File>) {
        if (results.size >= limit) return
        val children = dir.listFiles()?.sortedBy { it.name } ?: return
        for (child in children) {
            if (results.size >= limit) return
            if (child.isDirectory) {
                if (child.name.startsWith(".") || child.name == "node_modules" || child.name == "build") continue
                searchRecursive(child, globRegex, lowerPattern, limit, results)
            } else {
                val matches = if (globRegex != null) {
                    globRegex.matches(child.name)
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
                    '(' -> append("\\(")
                    ')' -> append("\\)")
                    '+' -> append("\\+")
                    '^' -> append("\\^")
                    '$' -> append("\\$")
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
