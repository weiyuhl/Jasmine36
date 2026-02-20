package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 列出 DEX 中的类
 * 移植自 AetherLink dex_list_classes
 */
object DexListClassesTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_list_classes",
        description = "Lists all classes in the DEX. Supports package name filtering and pagination.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("packageFilter", "Package name filter (e.g. \"com.example\")", StringType),
            ToolParameterDescriptor("offset", "Offset for pagination. Default 0", IntegerType),
            ToolParameterDescriptor("limit", "Max results. Default 100", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val packageFilter = obj["packageFilter"]?.jsonPrimitive?.content
        val offset = obj["offset"]?.jsonPrimitive?.int ?: 0
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 100

        return try {
            val session = DexSessionManager.getSession(sessionId)
            val result = session.listClasses(packageFilter, offset, limit)
            buildString {
                appendLine("Classes: ${result.total} total (showing ${result.classes.size} from offset ${result.offset})")
                result.classes.forEach { appendLine("  $it") }
                if (result.offset + result.classes.size < result.total) {
                    appendLine("... use offset=${result.offset + result.classes.size} for more")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 搜索 DEX 内容
 * 移植自 AetherLink dex_search
 */
object DexSearchTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_search",
        description = "Searches in opened DEX. Supports: class, package, method, field, string, int, code.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("query", "Search content", StringType),
            ToolParameterDescriptor("searchType", "Search type",
                EnumType(listOf("class", "package", "method", "field", "string", "int", "code")))
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("caseSensitive", "Case sensitive. Default false", BooleanType),
            ToolParameterDescriptor("maxResults", "Max results. Default 50", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val query = obj["query"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'query'"
        val searchType = obj["searchType"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'searchType'"
        val caseSensitive = obj["caseSensitive"]?.jsonPrimitive?.boolean ?: false
        val maxResults = obj["maxResults"]?.jsonPrimitive?.int ?: 50

        return try {
            val session = DexSessionManager.getSession(sessionId)
            val results = session.search(query, searchType, caseSensitive, maxResults)
            if (results.isEmpty()) return "No results found."
            buildString {
                appendLine("Found ${results.size} result(s):")
                results.forEach { appendLine("  [${it.type}] ${it.match}") }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 获取类的 Smali 代码
 * 移植自 AetherLink dex_get_class
 */
object DexGetClassTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_get_class",
        description = "Gets the Smali code of a class. Supports char limit for token control.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name (e.g. \"com.example.MainActivity\" or \"Lcom/example/MainActivity;\")", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("maxChars", "Max chars to return (0 = unlimited). Default 0", IntegerType),
            ToolParameterDescriptor("offset", "Char offset for pagination. Default 0", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val className = obj["className"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'className'"
        val maxChars = obj["maxChars"]?.jsonPrimitive?.int ?: 0
        val offset = obj["offset"]?.jsonPrimitive?.int ?: 0

        return try {
            val session = DexSessionManager.getSession(sessionId)
            session.getClassSmali(className, maxChars, offset)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 修改类的 Smali 代码
 * 移植自 AetherLink dex_modify_class
 */
object DexModifyClassTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_modify_class",
        description = "Modifies a class's Smali code (in memory only, call dex_save to persist).",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name", StringType),
            ToolParameterDescriptor("smaliContent", "New Smali code", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val className = obj["className"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'className'"
        val smaliContent = obj["smaliContent"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'smaliContent'"

        return try {
            val session = DexSessionManager.getSession(sessionId)
            session.modifyClass(className, smaliContent)
            "Class modified: $className (use dex_save to persist)"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 添加新类
 * 移植自 AetherLink dex_add_class
 */
object DexAddClassTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_add_class",
        description = "Adds a new class to the DEX. Provide complete Smali code.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "New class name (e.g. \"com.example.NewClass\")", StringType),
            ToolParameterDescriptor("smaliContent", "Complete Smali code", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val className = obj["className"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'className'"
        val smaliContent = obj["smaliContent"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'smaliContent'"

        return try {
            val session = DexSessionManager.getSession(sessionId)
            session.addClass(className, smaliContent)
            "Class added: $className"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 删除类
 * 移植自 AetherLink dex_delete_class
 */
object DexDeleteClassTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_delete_class",
        description = "Deletes a class from the DEX.",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("className", "Class name to delete", StringType)
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
            session.deleteClass(className)
            "Class deleted: $className"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 重命名类
 * 移植自 AetherLink dex_rename_class
 */
object DexRenameClassTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "dex_rename_class",
        description = "Renames a class (modifies class name and all references).",
        requiredParameters = listOf(
            ToolParameterDescriptor("sessionId", "Session ID", StringType),
            ToolParameterDescriptor("oldClassName", "Original class name", StringType),
            ToolParameterDescriptor("newClassName", "New class name", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val sessionId = obj["sessionId"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'sessionId'"
        val oldClassName = obj["oldClassName"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'oldClassName'"
        val newClassName = obj["newClassName"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'newClassName'"

        return try {
            val session = DexSessionManager.getSession(sessionId)
            session.renameClass(oldClassName, newClassName)
            "Renamed: $oldClassName -> $newClassName"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
