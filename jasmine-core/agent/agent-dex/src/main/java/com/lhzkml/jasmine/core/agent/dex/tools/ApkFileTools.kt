package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 列出 APK 中的所有文件
 * 移植自 AetherLink apk_list_files
 */
object ApkListFilesTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_list_files",
        description = "Lists all files in an APK. Supports filtering and pagination.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("filter", "Path filter (e.g. \"lib/\", \"assets/\", \".dex\", \".so\")", StringType),
            ToolParameterDescriptor("limit", "Max results. Default 100", IntegerType),
            ToolParameterDescriptor("offset", "Offset for pagination. Default 0", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val filter = obj["filter"]?.jsonPrimitive?.content ?: ""
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 100
        val offset = obj["offset"]?.jsonPrimitive?.int ?: 0

        return try {
            val result = DexSessionManager.listApkFiles(apkPath, filter, limit, offset)
            buildString {
                appendLine("Files in APK: ${result.total} total (showing ${result.files.size} from offset $offset)")
                result.files.forEach { f ->
                    val sizeStr = formatSize(f.size)
                    appendLine("  ${f.path} ($sizeStr)")
                }
                if (offset + result.files.size < result.total) {
                    appendLine("... use offset=${offset + result.files.size} for more")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 读取 APK 内文件
 * 移植自 AetherLink apk_read_file
 */
object ApkReadFileTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_read_file",
        description = "Reads a file from inside an APK (text or Base64 encoded).",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("filePath", "File path inside APK (e.g. \"assets/config.json\")", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("asBase64", "Return as Base64 (for binary files). Default false", BooleanType),
            ToolParameterDescriptor("maxBytes", "Max bytes to read (0 = unlimited). Default 0", IntegerType),
            ToolParameterDescriptor("offset", "Byte offset. Default 0", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val filePath = obj["filePath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'filePath'"
        val asBase64 = obj["asBase64"]?.jsonPrimitive?.boolean ?: false
        val maxBytes = obj["maxBytes"]?.jsonPrimitive?.int ?: 0
        val offset = obj["offset"]?.jsonPrimitive?.int ?: 0

        return try {
            DexSessionManager.readApkFile(apkPath, filePath, asBase64, maxBytes, offset)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * APK 内文本搜索
 * 移植自 AetherLink apk_search_text
 */
object ApkSearchTextTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_search_text",
        description = "Searches text content in APK files (without extracting). " +
            "Supports XML, JSON, TXT, SMALI etc. Auto-skips binary files.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("pattern", "Search pattern (text or regex)", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("fileExtensions", "File extension filter (e.g. [\".xml\", \".json\"])",
                ListType(StringType)),
            ToolParameterDescriptor("caseSensitive", "Case sensitive. Default false", BooleanType),
            ToolParameterDescriptor("isRegex", "Use regex. Default false", BooleanType),
            ToolParameterDescriptor("maxResults", "Max results. Default 50", IntegerType),
            ToolParameterDescriptor("contextLines", "Context lines. Default 2", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val pattern = obj["pattern"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'pattern'"
        val fileExtensions = obj["fileExtensions"]?.jsonArray?.map { it.jsonPrimitive.content }
        val caseSensitive = obj["caseSensitive"]?.jsonPrimitive?.boolean ?: false
        val isRegex = obj["isRegex"]?.jsonPrimitive?.boolean ?: false
        val maxResults = obj["maxResults"]?.jsonPrimitive?.int ?: 50
        val contextLines = obj["contextLines"]?.jsonPrimitive?.int ?: 2

        return try {
            val results = DexSessionManager.searchTextInApk(
                apkPath, pattern, fileExtensions, caseSensitive, isRegex, maxResults, contextLines
            )
            if (results.isEmpty()) return "No matches found."
            buildString {
                appendLine("Found ${results.size} match(es):")
                results.forEach { r ->
                    appendLine("--- ${r.filePath}:${r.lineNumber} ---")
                    appendLine(r.context)
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 删除 APK 内文件
 * 移植自 AetherLink apk_delete_file
 */
object ApkDeleteFileTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_delete_file",
        description = "Deletes a file from an APK (e.g. ad resources, unused .so libraries).",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("filePath", "File path to delete (e.g. \"lib/arm64-v8a/libad.so\")", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val filePath = obj["filePath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'filePath'"

        return try {
            val outputPath = DexSessionManager.deleteFileFromApk(apkPath, filePath)
            "Deleted $filePath. Output: $outputPath"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 添加文件到 APK
 * 移植自 AetherLink apk_add_file
 */
object ApkAddFileTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_add_file",
        description = "Adds or replaces a file in an APK (e.g. inject assets, .so libraries).",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("filePath", "Target path (e.g. \"assets/config.json\")", StringType),
            ToolParameterDescriptor("content", "File content (text or Base64 for binary)", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("isBase64", "Content is Base64 encoded. Default false", BooleanType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val filePath = obj["filePath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'filePath'"
        val content = obj["content"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'content'"
        val isBase64 = obj["isBase64"]?.jsonPrimitive?.boolean ?: false

        return try {
            val outputPath = DexSessionManager.addFileToApk(apkPath, filePath, content, isBase64)
            "Added $filePath. Output: $outputPath"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 列出 APK 资源文件
 * 移植自 AetherLink apk_list_resources
 */
object ApkListResourcesTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_list_resources",
        description = "Lists resource files (res/ directory) in an APK.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("filter", "Path filter (e.g. \"layout\", \"values\", \"drawable\")", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val filter = obj["filter"]?.jsonPrimitive?.content

        return try {
            val resources = DexSessionManager.listResources(apkPath, filter)
            if (resources.isEmpty()) return "No resources found."
            buildString {
                appendLine("Resources (${resources.size}):")
                resources.forEach { appendLine("  $it") }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        else -> "${size / (1024 * 1024)}MB"
    }
}
