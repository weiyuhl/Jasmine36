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
 * 创建新文件工具
 * 移植自 AetherLink 的 create_file
 * 与 WriteFileTool 的区别：默认不覆盖已有文件，需显式设置 overwrite
 * 自动创建父目录
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class CreateFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "create_file",
        description = "Creates a new file. Fails if the file already exists unless overwrite is set to true. " +
            "Automatically creates parent directories if needed. " +
            "Use this instead of write_file when you want to ensure you don't accidentally overwrite existing files. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the file to create", ToolParameterType.StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("content", "Initial file content. Default empty", ToolParameterType.StringType),
            ToolParameterDescriptor("overwrite", "Whether to overwrite if file exists. Default false", ToolParameterType.BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val content = obj["content"]?.jsonPrimitive?.content ?: ""
        val overwrite = obj["overwrite"]?.jsonPrimitive?.boolean ?: false

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"

        if (!overwrite && file.exists()) {
            return "Error: File already exists: $path. Set overwrite: true to overwrite."
        }

        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            val lines = if (content.isEmpty()) 0 else content.lines().size
            "Created: ${file.name} ($lines lines, ${content.length} chars)"
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
