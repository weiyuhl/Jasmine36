package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文件压缩工具
 * 将一个或多个文件/目录压缩为 ZIP 格式的压缩包。
 *
 * @param basePath 基础路径限制（安全沙箱），null 表示不限制
 */
class CompressFilesTool(
    private val basePath: String? = null
) : Tool() {

    override val descriptor = ToolDescriptor(
        name = "compress_files",
        description = "Compresses one or more files or directories into a ZIP archive. " +
            "Directories are recursively included. " +
            "Paths can be relative (resolved against workspace root) or absolute.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                "sources",
                "List of file/directory paths to compress",
                ToolParameterType.ListType(ToolParameterType.StringType)
            ),
            ToolParameterDescriptor(
                "output",
                "Output path for the ZIP file, e.g. \"archive.zip\"",
                ToolParameterType.StringType
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject

        // 解析 sources 参数（JSON 数组）
        val sourcePaths: List<String> = try {
            val sourcesElement = obj["sources"] ?: return "Error: Missing parameter 'sources'"
            sourcesElement.jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            return "Error: Invalid 'sources' parameter: ${e.message}"
        }

        val output = obj["output"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'output'"

        if (sourcePaths.isEmpty()) return "Error: No source paths provided"

        val outputFile = resolveFile(output) ?: return "Error: Output path not allowed: $output"
        if (outputFile.exists()) return "Error: Output file already exists: $output"
        if (!output.endsWith(".zip", ignoreCase = true)) {
            return "Error: Output file must have .zip extension"
        }

        // 验证所有源路径
        val sourceFiles = mutableListOf<Pair<String, File>>()
        for (path in sourcePaths) {
            val file = resolveFile(path) ?: return "Error: Path not allowed: $path"
            if (!file.exists()) return "Error: Source does not exist: $path"
            sourceFiles.add(path to file)
        }

        return try {
            outputFile.parentFile?.mkdirs()
            var fileCount = 0
            var totalBytes = 0L

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
                for ((originalPath, file) in sourceFiles) {
                    if (file.isDirectory) {
                        val result = addDirectoryToZip(zos, file, file.name)
                        fileCount += result.first
                        totalBytes += result.second
                    } else {
                        addFileToZip(zos, file, file.name)
                        fileCount++
                        totalBytes += file.length()
                    }
                }
            }

            val zipSize = outputFile.length()
            val ratio = if (totalBytes > 0) {
                "%.1f%%".format((1.0 - zipSize.toDouble() / totalBytes) * 100)
            } else "N/A"

            "Compressed $fileCount file(s) into ${outputFile.path} " +
                "(${formatSize(totalBytes)} -> ${formatSize(zipSize)}, saved $ratio)"
        } catch (e: Exception) {
            outputFile.delete()
            "Error: ${e.message}"
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            fis.copyTo(zos, bufferSize = 8192)
        }
        zos.closeEntry()
    }

    private fun addDirectoryToZip(zos: ZipOutputStream, dir: File, prefix: String): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        val files = dir.listFiles() ?: return 0 to 0L
        for (file in files) {
            val entryName = "$prefix/${file.name}"
            if (file.isDirectory) {
                val result = addDirectoryToZip(zos, file, entryName)
                count += result.first
                bytes += result.second
            } else {
                addFileToZip(zos, file, entryName)
                count++
                bytes += file.length()
            }
        }
        return count to bytes
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

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
