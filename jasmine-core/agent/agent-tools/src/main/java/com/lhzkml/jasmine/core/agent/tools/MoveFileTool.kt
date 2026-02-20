package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 移动/重命名文件或文件夹工具
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class MoveFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "move_file",
        description = "Moves or renames a file or directory. Creates parent directories of destination if needed. " +
            "Paths can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("source", "Source path of the file or directory to move", ToolParameterType.StringType),
            ToolParameterDescriptor("destination", "Destination path where the file or directory will be moved to", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val source = obj["source"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'source'"
        val destination = obj["destination"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'destination'"

        val srcFile = resolveFile(source) ?: return "Error: Source path not allowed: $source"
        val dstFile = resolveFile(destination) ?: return "Error: Destination path not allowed: $destination"

        if (!srcFile.exists()) return "Error: Source does not exist: $source"
        if (dstFile.exists()) return "Error: Destination already exists: $destination"

        return try {
            dstFile.parentFile?.mkdirs()
            val success = srcFile.renameTo(dstFile)
            if (success) {
                "Moved: ${srcFile.name} -> ${dstFile.path}"
            } else {
                // renameTo 跨文件系统可能失败，用 copy + delete
                srcFile.copyRecursively(dstFile, overwrite = false)
                srcFile.deleteRecursively()
                "Moved: ${srcFile.name} -> ${dstFile.path}"
            }
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
