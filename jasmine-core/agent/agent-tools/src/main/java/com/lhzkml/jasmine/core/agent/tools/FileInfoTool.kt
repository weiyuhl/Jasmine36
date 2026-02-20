package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 获取文件/目录详细信息工具
 * 返回大小、修改时间、类型、权限等元数据
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class FileInfoTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "file_info",
        description = "Gets detailed information about a file or directory: size, last modified time, type, permissions, " +
            "and for directories the number of children. " +
            "Path can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor("path", "Path to the file or directory", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val path = obj["path"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'path'"

        val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
        if (!file.exists()) return "Error: Path does not exist: $path"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return try {
            buildString {
                appendLine("Path: ${file.absolutePath}")
                appendLine("Name: ${file.name}")
                appendLine("Type: ${if (file.isDirectory) "directory" else "file"}")
                appendLine("Size: ${formatSize(file)}")
                appendLine("Modified: ${dateFormat.format(Date(file.lastModified()))}")
                appendLine("Readable: ${file.canRead()}")
                appendLine("Writable: ${file.canWrite()}")
                appendLine("Executable: ${file.canExecute()}")
                if (file.isDirectory) {
                    val children = file.listFiles()
                    val fileCount = children?.count { it.isFile } ?: 0
                    val dirCount = children?.count { it.isDirectory } ?: 0
                    appendLine("Children: $fileCount files, $dirCount directories")
                } else {
                    appendLine("Extension: ${file.extension.ifEmpty { "(none)" }}")
                    appendLine("Lines: ${file.readLines().size}")
                    appendLine("Hash(MD5): ${computeMD5(file)}")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun formatSize(file: File): String {
        val size = if (file.isDirectory) dirSize(file) else file.length()
        return when {
            size < 1024 -> "${size} B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun dirSize(dir: File): Long {
        return dir.listFiles()?.sumOf { if (it.isDirectory) dirSize(it) else it.length() } ?: 0
    }

    private fun computeMD5(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = file.readBytes()
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "(error: ${e.message})"
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
