package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 文件内容查找替换工具
 * 移植自 AetherLink 的 replace_in_file
 * 支持普通字符串和正则表达式，支持替换全部或仅第一个匹配
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class ReplaceInFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "replace_in_file",
        description = "Finds and replaces content in a file. Supports plain text and regular expressions. " +
            "Returns the number of replacements made. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the target file", ToolParameterType.StringType),
            ToolParameterDescriptor("search", "String or regex pattern to search for", ToolParameterType.StringType),
            ToolParameterDescriptor("replace", "Replacement content", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("is_regex", "Whether search is a regex pattern. Default false", ToolParameterType.BooleanType),
            ToolParameterDescriptor("replace_all", "Whether to replace all occurrences. Default true", ToolParameterType.BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val search = obj["search"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'search'"
        val replace = obj["replace"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'replace'"
        val isRegex = obj["is_regex"]?.jsonPrimitive?.boolean ?: false
        val replaceAll = obj["replace_all"]?.jsonPrimitive?.boolean ?: true

        if (search.isEmpty()) return "Error: search must not be empty"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: File not found: $path"
        if (!file.isFile) return "Error: Not a file: $path"

        return try {
            val content = file.readText()

            val regex = if (isRegex) {
                try {
                    Regex(search)
                } catch (e: Exception) {
                    return "Error: Invalid regex: ${e.message}"
                }
            } else {
                Regex(Regex.escape(search))
            }

            val matches = regex.findAll(content).toList()
            if (matches.isEmpty()) {
                return "No matches found for: $search"
            }

            val replacementCount: Int
            val newContent: String

            if (replaceAll) {
                replacementCount = matches.size
                newContent = regex.replace(content, replace)
            } else {
                replacementCount = 1
                newContent = regex.replaceFirst(content, replace)
            }

            file.writeText(newContent)
            "Replaced $replacementCount occurrence(s) in ${file.name}"
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
