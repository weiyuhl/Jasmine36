package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 搜索 ARSC 字符串
 * 移植自 AetherLink apk_search_arsc_strings
 */
object ApkSearchArscStringsTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_search_arsc_strings",
        description = "Searches string resources in resources.arsc. Useful for finding hardcoded strings, URLs, keys.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("pattern", "Search pattern", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("limit", "Max results. Default 50", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val pattern = obj["pattern"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'pattern'"
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 50

        return try {
            val results = DexSessionManager.searchArscStrings(apkPath, pattern, limit)
            if (results.isEmpty()) return "No ARSC strings matching '$pattern'"
            buildString {
                appendLine("Found ${results.size} ARSC string(s):")
                results.forEach { appendLine("  \"$it\"") }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 搜索 ARSC 资源
 * 移植自 AetherLink apk_search_arsc_resources
 */
object ApkSearchArscResourcesTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_search_arsc_resources",
        description = "Searches resources in resources.arsc by name pattern and optional type filter.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("pattern", "Search pattern", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("type", "Resource type filter (e.g. \"string\", \"drawable\", \"layout\")", StringType),
            ToolParameterDescriptor("limit", "Max results. Default 50", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val pattern = obj["pattern"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'pattern'"
        val type = obj["type"]?.jsonPrimitive?.content
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 50

        return try {
            val results = DexSessionManager.searchArscResources(apkPath, pattern, type, limit)
            if (results.isEmpty()) return "No ARSC resources matching '$pattern'"
            buildString {
                appendLine("Found ${results.size} ARSC resource(s):")
                results.forEach { appendLine("  $it") }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
