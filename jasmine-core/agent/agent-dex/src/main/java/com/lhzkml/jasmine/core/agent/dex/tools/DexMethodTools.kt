package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.StringType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 获取方法 Smali
 * 移植自 AetherLink dex_get_method
 */
object DexGetMethodTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_get_method",
        description = "Gets the Smali code of a single method. Useful for large classes where you only need one method.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name", StringType),
            ToolParameterDescriptor("methodName", "Method name (e.g. \"onCreate\" or \"<init>\")", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("methodSignature", "Method signature for overloaded methods (e.g. \"(Landroid/os/Bundle;)V\")", StringType)
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
        val methodSignature = obj["methodSignature"]?.jsonPrimitive?.content

        return try {
            val session = DexSessionManager.getSession(sessionId)
            session.getMethod(className, methodName, methodSignature)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 修改方法
 * 移植自 AetherLink dex_modify_method
 */
object DexModifyMethodTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_modify_method",
        description = "Modifies a single method's Smali code. Only provide the method code, it auto-replaces the original.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name", StringType),
            ToolParameterDescriptor("methodName", "Method name", StringType),
            ToolParameterDescriptor("newMethodCode", "New method Smali code (from .method to .end method)", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("methodSignature", "Method signature for overloaded methods", StringType)
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
        val newMethodCode = obj["newMethodCode"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'newMethodCode'"
        val methodSignature = obj["methodSignature"]?.jsonPrimitive?.content

        return try {
            val session = DexSessionManager.getSession(sessionId)
            session.modifyMethod(className, methodName, methodSignature, newMethodCode)
            "Method modified: $className.$methodName"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 列出方法
 * 移植自 AetherLink dex_list_methods
 */
object DexListMethodsTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_list_methods",
        description = "Lists all methods of a class with name, signature, and access modifiers.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name", StringType)
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
            val methods = session.listMethods(className)
            if (methods.isEmpty()) return "No methods found in $className"
            buildString {
                appendLine("Methods in $className (${methods.size}):")
                methods.forEach { m ->
                    val params = m.parameters.joinToString(", ")
                    appendLine("  ${accessFlagsToString(m.accessFlags)} ${m.name}($params) -> ${m.returnType}")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 列出字段
 * 移植自 AetherLink dex_list_fields
 */
object DexListFieldsTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_list_fields",
        description = "Lists all fields of a class with name, type, and access modifiers.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name", StringType)
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
            val fields = session.listFields(className)
            if (fields.isEmpty()) return "No fields found in $className"
            buildString {
                appendLine("Fields in $className (${fields.size}):")
                fields.forEach { f ->
                    appendLine("  ${accessFlagsToString(f.accessFlags)} ${f.name}: ${f.type}")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 列出字符串池
 * 移植自 AetherLink dex_list_strings
 */
object DexListStringsTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_list_strings",
        description = "Lists strings in the DEX string pool. Supports filtering and limiting.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("filter", "Filter string (contains match)", StringType),
            ToolParameterDescriptor("limit", "Max results. Default 100", com.lhzkml.jasmine.core.prompt.model.ToolParameterType.IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val filter = obj["filter"]?.jsonPrimitive?.content
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 100

        return try {
            val session = DexSessionManager.getSession(sessionId)
            val strings = session.listStrings(filter, limit)
            if (strings.isEmpty()) return "No strings found."
            buildString {
                appendLine("Strings (${strings.size}):")
                strings.forEach { appendLine("  \"$it\"") }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

// 辅助函数
private fun accessFlagsToString(flags: Int): String {
    val parts = mutableListOf<String>()
    if (flags and 0x0001 != 0) parts.add("public")
    if (flags and 0x0002 != 0) parts.add("private")
    if (flags and 0x0004 != 0) parts.add("protected")
    if (flags and 0x0008 != 0) parts.add("static")
    if (flags and 0x0010 != 0) parts.add("final")
    if (flags and 0x0020 != 0) parts.add("synchronized")
    if (flags and 0x0400 != 0) parts.add("abstract")
    if (flags and 0x0100 != 0) parts.add("native")
    return parts.joinToString(" ")
}
