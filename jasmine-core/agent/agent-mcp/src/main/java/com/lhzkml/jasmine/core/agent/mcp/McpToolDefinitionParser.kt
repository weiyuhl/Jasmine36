package com.lhzkml.jasmine.core.agent.mcp

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType

/**
 * MCP 工具定义解析器接�?
 * 完整移植 koog �?McpToolDescriptorParser�?
 * �?MCP 服务器返回的工具定义转换�?jasmine �?ToolDescriptor 格式�?
 */
interface McpToolDefinitionParser {
    /**
     * 解析 MCP 工具定义�?ToolDescriptor
     */
    fun parse(definition: McpToolDefinition): ToolDescriptor
}

/**
 * 默认 MCP 工具定义解析�?
 * 完整移植 koog �?DefaultMcpToolDescriptorParser�?
 * 支持递归解析嵌套�?JSON Schema 类型�?
 */
object DefaultMcpToolDefinitionParser : McpToolDefinitionParser {

    private const val MAX_DEPTH = 30

    override fun parse(definition: McpToolDefinition): ToolDescriptor {
        val requiredNames = definition.inputSchema?.required?.toSet() ?: emptySet()
        val allParams = definition.inputSchema?.properties?.map { (name, schema) ->
            ToolParameterDescriptor(
                name = name,
                description = schema.description ?: "",
                type = parseType(schema)
            )
        } ?: emptyList()

        return ToolDescriptor(
            name = definition.name,
            description = definition.description ?: "",
            requiredParameters = allParams.filter { it.name in requiredNames },
            optionalParameters = allParams.filter { it.name !in requiredNames }
        )
    }

    /**
     * 递归解析属性类型为 ToolParameterType
     * 参�?koog �?parseParameterType
     */
    private fun parseType(schema: McpPropertySchema, depth: Int = 0): ToolParameterType {
        if (depth > MAX_DEPTH) {
            throw IllegalArgumentException(
                "Maximum recursion depth ($MAX_DEPTH) exceeded. Possible circular reference."
            )
        }

        return when (schema.type.lowercase()) {
            "string" -> {
                if (schema.enum != null) {
                    ToolParameterType.EnumType(schema.enum)
                } else {
                    ToolParameterType.StringType
                }
            }
            "integer" -> ToolParameterType.IntegerType
            "number" -> ToolParameterType.FloatType
            "boolean" -> ToolParameterType.BooleanType
            "array" -> {
                val itemType = schema.items?.let { parseType(it, depth + 1) } ?: ToolParameterType.StringType
                ToolParameterType.ListType(itemType)
            }
            "object" -> {
                if (schema.properties != null) {
                    val props = schema.properties.map { (name, prop) ->
                        ToolParameterDescriptor(
                            name = name,
                            description = prop.description ?: "",
                            type = parseType(prop, depth + 1)
                        )
                    }
                    ToolParameterType.ObjectType(props)
                } else {
                    ToolParameterType.ObjectType(emptyList())
                }
            }
            else -> ToolParameterType.StringType
        }
    }
}
