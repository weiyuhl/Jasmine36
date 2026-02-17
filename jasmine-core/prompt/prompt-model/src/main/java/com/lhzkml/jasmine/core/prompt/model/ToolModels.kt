package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 工具参数类型
 * 参考 koog 的 ToolParameterType 设计
 */
sealed class ToolParameterType(val name: String) {
    data object StringType : ToolParameterType("STRING")
    data object IntegerType : ToolParameterType("INT")
    data object FloatType : ToolParameterType("FLOAT")
    data object BooleanType : ToolParameterType("BOOLEAN")

    /** 枚举类型 */
    data class EnumType(val entries: List<String>) : ToolParameterType("ENUM")

    /** 数组类型 */
    data class ListType(val itemsType: ToolParameterType) : ToolParameterType("ARRAY")

    /** 对象类型 */
    data class ObjectType(
        val properties: List<ToolParameterDescriptor>,
        val requiredProperties: List<String> = emptyList()
    ) : ToolParameterType("OBJECT")
}

/**
 * 工具参数描述
 * 参考 koog 的 ToolParameterDescriptor
 *
 * @param name 参数名（snake_case）
 * @param description 参数描述
 * @param type 参数类型
 */
data class ToolParameterDescriptor(
    val name: String,
    val description: String,
    val type: ToolParameterType
)

/**
 * 工具描述
 * 参考 koog 的 ToolDescriptor，用于向 LLM 声明可用的工具
 *
 * @param name 工具名称（a-z, A-Z, 0-9, 下划线、连字符，最长 64 字符）
 * @param description 工具功能描述，LLM 根据此描述决定何时调用
 * @param requiredParameters 必填参数列表
 * @param optionalParameters 可选参数列表
 */
data class ToolDescriptor(
    val name: String,
    val description: String,
    val requiredParameters: List<ToolParameterDescriptor> = emptyList(),
    val optionalParameters: List<ToolParameterDescriptor> = emptyList()
) {
    /**
     * 转换为 OpenAI 兼容的 JSON Schema 格式
     */
    fun toJsonSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            (requiredParameters + optionalParameters).forEach { param ->
                put(param.name, param.toJsonSchema())
            }
        }
        putJsonArray("required") {
            requiredParameters.forEach { add(it.name) }
        }
    }
}

/**
 * LLM 返回的工具调用请求
 *
 * @param id 调用 ID（用于关联结果）
 * @param name 工具名称
 * @param arguments 参数 JSON 字符串
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * 工具执行结果（回传给 LLM）
 *
 * @param callId 对应的 ToolCall.id
 * @param name 工具名称
 * @param content 执行结果内容
 */
data class ToolResult(
    val callId: String,
    val name: String,
    val content: String
)

// ========== 内部辅助 ==========

private fun ToolParameterDescriptor.toJsonSchema(): JsonObject = buildJsonObject {
    put("description", description)
    fillJsonSchema(type)
}

private fun JsonObjectBuilder.fillJsonSchema(type: ToolParameterType) {
    when (type) {
        ToolParameterType.BooleanType -> put("type", "boolean")
        ToolParameterType.FloatType -> put("type", "number")
        ToolParameterType.IntegerType -> put("type", "integer")
        ToolParameterType.StringType -> put("type", "string")
        is ToolParameterType.EnumType -> {
            put("type", "string")
            putJsonArray("enum") {
                type.entries.forEach { add(it) }
            }
        }
        is ToolParameterType.ListType -> {
            put("type", "array")
            putJsonObject("items") { fillJsonSchema(type.itemsType) }
        }
        is ToolParameterType.ObjectType -> {
            put("type", "object")
            putJsonObject("properties") {
                type.properties.forEach { prop ->
                    putJsonObject(prop.name) {
                        fillJsonSchema(prop.type)
                        put("description", prop.description)
                    }
                }
            }
        }
    }
}
