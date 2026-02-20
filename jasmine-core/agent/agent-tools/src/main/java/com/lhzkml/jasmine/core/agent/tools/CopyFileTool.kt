package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 复制文件或文件夹工具
 * 支持复制单个文件或递归复制整个目录
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class CopyFileTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "copy_file",
        description = "Copies a file or directory to a new location. For directories, recursively copies all contents. " +
            "Creates parent directories of destination if needed. " +
            "Paths can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("source", "Source path of the file or directory to copy", ToolParameterType.StringType),
            ToolParameterDescriptor("destination", "Destination path where the copy will be created", ToolParameterType.StringType)
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
            if (srcFile.isDirectory) {
                srcFile.copyRecursively(dstFile, overwrite = false)
                "Copied directory: ${srcFile.name} -> ${dstFile.path}"
            } else {
                srcFile.copyTo(dstFile, overwrite = false)
                "Copied: ${srcFile.name} -> ${dstFile.path}"
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
