package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 重命名文件或目录工具
 * 移植自 AetherLink 的 rename_file
 * 只改变名称，不改变位置。new_name 不能包含路径分隔符
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class RenameFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "rename_file",
        description = "Renames a file or directory. Only changes the name, not the location. " +
            "new_name must not contain path separators. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the file or directory to rename", ToolParameterType.StringType),
            ToolParameterDescriptor("new_name", "New name (file name only, no path separators)", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"
        val newName = obj["new_name"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'new_name'"

        if (newName.contains('/') || newName.contains('\\')) {
            return "Error: new_name must not contain path separators, only a file/directory name"
        }
        if (newName.isBlank()) return "Error: new_name must not be blank"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: Path does not exist: $path"

        val newFile = File(file.parentFile, newName)
        if (newFile.exists()) return "Error: Target already exists: ${newFile.path}"

        return try {
            val success = file.renameTo(newFile)
            if (success) {
                "Renamed: ${file.name} -> $newName"
            } else {
                "Error: Rename failed (system returned false)"
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
