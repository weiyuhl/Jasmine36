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
 * 参考 koog 的 ListDirectoryTool
 * 列出目录内容，支持深度控制和 glob 过滤
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class ListDirectoryTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "list_directory",
        description = "Lists directory contents as a tree. Use depth to control traversal depth (1 = direct children). " +
            "Optionally filter by glob pattern (e.g. '*.kt', '**/*.java'). " +
            "Returns structured tree with file/folder names and metadata. Read-only, does not modify filesystem.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Absolute path to the directory to list", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("depth", "Maximum traversal depth (>0). Default 1", ToolParameterType.IntegerType),
            ToolParameterDescriptor("filter", "Glob pattern to filter results (e.g. '**/*.kt'). Case-insensitive", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val depth = obj["depth"]?.jsonPrimitive?.int ?: 1
        val filter = obj["filter"]?.jsonPrimitive?.content

        if (depth < 1) return "Error: Depth must be at least 1"

        val dir = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!dir.exists()) return "Error: Path does not exist: $path"
        if (!dir.isDirectory) return "Error: Not a directory: $path"

        val filterRegex = filter?.let { globToRegex(it) }

        return try {
            val sb = StringBuilder()
            sb.appendLine("Directory: ${dir.name}/")
            listTree(dir, dir, depth, filterRegex, sb, "")
            sb.toString().trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun listTree(
        root: File, current: File, remainingDepth: Int,
        filter: Regex?, sb: StringBuilder, indent: String
    ) {
        if (remainingDepth <= 0) return
        val children = current.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return

        for (child in children) {
            val relativePath = child.relativeTo(root).path.replace('\\', '/')
            if (filter != null && !child.isDirectory && !filter.matches(relativePath)) continue

            if (child.isDirectory) {
                sb.appendLine("$indent${child.name}/")
                listTree(root, child, remainingDepth - 1, filter, sb, "$indent  ")
            } else {
                val size = child.length()
                val sizeStr = when {
                    size < 1024 -> "${size}B"
                    size < 1024 * 1024 -> "${size / 1024}KB"
                    else -> "${size / (1024 * 1024)}MB"
                }
                sb.appendLine("$indent${child.name} ($sizeStr)")
            }
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
                            if (i < glob.length && glob[i] == '/') i++ // skip separator after **
                            continue
                        } else {
                            append("[^/]*")
                        }
                    }
                    '?' -> append("[^/]")
                    '.' -> append("\\.")
                    '/' -> append("/")
                    else -> append(glob[i])
                }
                i++
            }
        }
        return Regex(regex, RegexOption.IGNORE_CASE)
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
