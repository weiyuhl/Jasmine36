package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 参考 koog 的 WriteFileTool
 * 写入文本内容到文件，自动创建父目录，覆盖已有内容
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class WriteFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "write_file",
        description = "Writes text content to a file. Creates parent directories if needed and overwrites existing content. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the target file (relative to workspace root, or absolute)", ToolParameterType.StringType),
            ToolParameterDescriptor("content", "Text content to write (must not be empty). Overwrites existing content completely", ToolParameterType.StringType)
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

        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            "Written: ${file.name} (${content.length} chars)"
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
