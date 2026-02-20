package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.StringType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 打开 APK 查看 DEX 文件列表
 * 移植自 AetherLink dex_open_apk
 */
object DexOpenApkTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_open_apk",
        description = "Opens an APK file and lists all DEX files inside. This is the first step for DEX editing.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "Full path to the APK file", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"

        return try {
            val dexFiles = DexSessionManager.listDexFiles(apkPath)
            buildString {
                appendLine("APK: $apkPath")
                appendLine("DEX files found: ${dexFiles.size}")
                dexFiles.forEach { appendLine("  $it") }
                appendLine("Use dex_open to open specific DEX files for editing.")
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
