package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 删除文件或文件夹工具
 * 支持删除单个文件或递归删除整个目录
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class DeleteFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "delete_file",
        description = "Deletes a file or directory. For directories, recursively deletes all contents. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the file or directory to delete", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: Path does not exist: $path"

        return try {
            if (file.isDirectory) {
                val count = countFiles(file)
                file.deleteRecursively()
                "Deleted directory: ${file.name} ($count items)"
            } else {
                file.delete()
                "Deleted: ${file.name}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun countFiles(dir: File): Int {
        return dir.listFiles()?.sumOf { if (it.isDirectory) countFiles(it) + 1 else 1 } ?: 0
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
