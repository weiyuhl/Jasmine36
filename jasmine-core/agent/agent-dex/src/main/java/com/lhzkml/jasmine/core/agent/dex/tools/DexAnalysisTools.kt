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
 * 查找方法交叉引用
 * 移植自 AetherLink dex_find_method_xrefs
 */
object DexFindMethodXrefsTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_find_method_xrefs",
        description = "Finds all cross-references to a method. Shows which classes call this method.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name containing the method", StringType),
            ToolParameterDescriptor("methodName", "Method name to find references for", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val className = obj["className"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'className'"
        val methodName = obj["methodName"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'methodName'"

        return try {
            val session = DexSessionManager.getSession(sessionId)
            val xrefs = session.findMethodXrefs(className, methodName)
            if (xrefs.isEmpty()) return "No cross-references found for $className.$methodName"
            buildString {
                appendLine("Found ${xrefs.size} cross-reference(s) for $className.$methodName:")
                xrefs.forEach { x ->
                    appendLine("  ${x.fromClass}:${x.lineNumber} - ${x.instruction}")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 查找字段交叉引用
 * 移植自 AetherLink dex_find_field_xrefs
 */
object DexFindFieldXrefsTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_find_field_xrefs",
        description = "Finds all cross-references to a field. Shows which classes access this field.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name containing the field", StringType),
            ToolParameterDescriptor("fieldName", "Field name to find references for", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val className = obj["className"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'className'"
        val fieldName = obj["fieldName"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'fieldName'"

        return try {
            val session = DexSessionManager.getSession(sessionId)
            val xrefs = session.findFieldXrefs(className, fieldName)
            if (xrefs.isEmpty()) return "No cross-references found for $className.$fieldName"
            buildString {
                appendLine("Found ${xrefs.size} cross-reference(s) for $className.$fieldName:")
                xrefs.forEach { x ->
                    appendLine("  ${x.fromClass}:${x.lineNumber} - ${x.instruction}")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * Smali 转 Java 伪代码
 * 移植自 AetherLink dex_smali_to_java
 */
object DexSmaliToJavaTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_smali_to_java",
        description = "Converts a class's Smali code to Java pseudo-code for better readability.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name to convert", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val className = obj["className"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'className'"

        return try {
            val session = DexSessionManager.getSession(sessionId)
            session.smaliToJava(className)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
