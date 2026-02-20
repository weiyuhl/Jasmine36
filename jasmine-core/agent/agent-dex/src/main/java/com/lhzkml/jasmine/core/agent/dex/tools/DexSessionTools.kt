package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 打开 DEX 文件创建编辑会话
 * 移植自 AetherLink dex_open
 */
object DexOpenTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_open",
        description = "Opens specified DEX files for editing. Returns a session ID for subsequent operations.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("dexFiles", "DEX file names list, e.g. [\"classes.dex\", \"classes2.dex\"]",
                ListType(StringType))
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val dexFiles = obj["dexFiles"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: return "Error: Missing parameter 'dexFiles'"

        return try {
            val session = DexSessionManager.openSession(apkPath, dexFiles)
            val classList = session.listClasses(limit = 0)
            "Session opened: ${session.id}\nLoaded ${classList.total} classes from ${dexFiles.size} DEX file(s)"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 保存修改到 APK
 * 移植自 AetherLink dex_save
 */
object DexSaveTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_save",
        description = "Compiles modified Smali code and saves DEX back to APK. User needs to re-sign the APK.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"

        return try {
            val session = DexSessionManager.getSession(sessionId)
            val outputPath = session.saveToApk()
            "Saved to: $outputPath\nNote: APK needs to be re-signed before installation."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 关闭会话
 * 移植自 AetherLink dex_close
 */
object DexCloseTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_close",
        description = "Closes a DEX editing session and releases resources.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"

        return try {
            DexSessionManager.closeSession(sessionId)
            "Session closed: $sessionId"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 列出所有会话
 * 移植自 AetherLink dex_list_sessions
 */
object DexListSessionsTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_list_sessions",
        description = "Lists all open DEX editing sessions."
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val sessions = DexSessionManager.listSessions()
            if (sessions.isEmpty()) return "No open sessions."
            buildString {
                appendLine("Open sessions: ${sessions.size}")
                for (s in sessions) {
                    appendLine("  [${s.id}] ${s.apkPath} (${s.dexFiles.joinToString(", ")})")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
