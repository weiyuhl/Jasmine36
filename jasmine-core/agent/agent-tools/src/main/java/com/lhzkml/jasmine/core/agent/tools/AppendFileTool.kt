package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 追加内容到文件末尾工具
 * 不覆盖已有内容，自动处理换行
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class AppendFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "append_file",
        description = "Appends text content to the end of an existing file without overwriting. " +
            "Automatically adds a newline before the new content if the file doesn't end with one. " +
            "The file must already exist. Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the existing file", ToolParameterType.StringType),
            ToolParameterDescriptor("content", "Text content to append to the end of the file", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val content = obj["content"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'content'"

        if (content.isEmpty()) return "Error: Content must not be empty"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: File does not exist: $path"
        if (!file.isFile) return "Error: Not a file: $path"

        return try {
            val existing = file.readText()
            val separator = if (existing.isNotEmpty() && !existing.endsWith("\n")) "\n" else ""
            file.appendText(separator + content)
            "Appended ${content.length} chars to: ${file.name}"
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
