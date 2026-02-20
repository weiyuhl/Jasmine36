package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 获取 AndroidManifest.xml
 * 移植自 AetherLink apk_get_manifest
 */
object ApkGetManifestTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_get_manifest",
        description = "Gets the AndroidManifest.xml content from an APK. Supports pagination.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("maxChars", "Max chars to return (0 = unlimited). Default 0", IntegerType),
            ToolParameterDescriptor("offset", "Char offset for pagination. Default 0", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val maxChars = obj["maxChars"]?.jsonPrimitive?.int ?: 0
        val offset = obj["offset"]?.jsonPrimitive?.int ?: 0

        return try {
            val result = DexSessionManager.getManifest(apkPath, maxChars, offset)
            if (maxChars > 0 || offset > 0) {
                buildString {
                    appendLine("Total length: ${result.totalLength}")
                    appendLine("Offset: ${result.offset}")
                    appendLine("Has more: ${result.hasMore}")
                    appendLine("---")
                    append(result.content)
                }
            } else {
                result.content
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 修改 AndroidManifest.xml
 * 移植自 AetherLink apk_modify_manifest
 */
object ApkModifyManifestTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_modify_manifest",
        description = "Replaces the AndroidManifest.xml in an APK with new content. APK needs re-signing.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("newManifest", "New AndroidManifest.xml content", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val newManifest = obj["newManifest"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'newManifest'"

        return try {
            val outputPath = DexSessionManager.modifyManifest(apkPath, newManifest)
            "AndroidManifest.xml modified. Output: $outputPath\nNote: APK needs re-signing."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 替换 Manifest 中的字符串
 * 移植自 AetherLink apk_replace_in_manifest
 */
object ApkReplaceInManifestTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_replace_in_manifest",
        description = "Replaces strings in AndroidManifest.xml. Useful for changing package names, version codes, etc.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("replacements", "Array of {oldValue, newValue} pairs",
                ListType(ObjectType(
                    properties = listOf(
                        ToolParameterDescriptor("oldValue", "Original string to find", StringType),
                        ToolParameterDescriptor("newValue", "Replacement string", StringType)
                    ),
                    requiredProperties = listOf("oldValue", "newValue")
                )))
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val replacementsArr = obj["replacements"]?.jsonArray
            ?: return "Error: Missing parameter 'replacements'"

        val replacements = replacementsArr.map { item ->
            val o = item.jsonObject
            val oldValue = o["oldValue"]?.jsonPrimitive?.content ?: ""
            val newValue = o["newValue"]?.jsonPrimitive?.content ?: ""
            Pair(oldValue, newValue)
        }

        return try {
            val result = DexSessionManager.replaceInManifest(apkPath, replacements)
            "Replaced ${result.replacedCount} occurrence(s). APK needs re-signing."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 获取资源文件内容
 * 移植自 AetherLink apk_get_resource
 */
object ApkGetResourceTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_get_resource",
        description = "Gets the content of a resource file (e.g. res/layout/activity_main.xml).",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("resourcePath", "Resource path (e.g. \"res/layout/activity_main.xml\")", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("maxChars", "Max chars to return (0 = unlimited). Default 0", IntegerType),
            ToolParameterDescriptor("offset", "Char offset for pagination. Default 0", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val resourcePath = obj["resourcePath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'resourcePath'"
        val maxChars = obj["maxChars"]?.jsonPrimitive?.int ?: 0
        val offset = obj["offset"]?.jsonPrimitive?.int ?: 0

        return try {
            DexSessionManager.getResource(apkPath, resourcePath, maxChars, offset)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 修改资源文件
 * 移植自 AetherLink apk_modify_resource
 */
object ApkModifyResourceTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_modify_resource",
        description = "Modifies a resource file in the APK. APK needs re-signing.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("resourcePath", "Resource path (e.g. \"res/layout/activity_main.xml\")", StringType),
            ToolParameterDescriptor("newContent", "New resource content", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val resourcePath = obj["resourcePath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'resourcePath'"
        val newContent = obj["newContent"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'newContent'"

        return try {
            val outputPath = DexSessionManager.modifyResource(apkPath, resourcePath, newContent)
            "Resource $resourcePath modified. Output: $outputPath\nNote: APK needs re-signing."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
