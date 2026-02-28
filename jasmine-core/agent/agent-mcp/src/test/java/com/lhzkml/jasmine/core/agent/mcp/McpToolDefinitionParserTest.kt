package com.lhzkml.jasmine.core.agent.mcp

import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import org.junit.Assert.*
import org.junit.Test

class McpToolDefinitionParserTest {

    @Test
    fun `parse simple tool with string params`() {
        val def = McpToolDefinition(
            name = "search",
            description = "Search the web",
            inputSchema = McpInputSchema(
                properties = mapOf(
                    "query" to McpPropertySchema(type = "string", description = "Search query"),
                    "limit" to McpPropertySchema(type = "integer", description = "Max results")
                ),
                required = listOf("query")
            )
        )
        val descriptor = DefaultMcpToolDefinitionParser.parse(def)
        assertEquals("search", descriptor.name)
        assertEquals("Search the web", descriptor.description)
        assertEquals(1, descriptor.requiredParameters.size)
        assertEquals("query", descriptor.requiredParameters[0].name)
        assertEquals(1, descriptor.optionalParameters.size)
        assertEquals("limit", descriptor.optionalParameters[0].name)
    }

    @Test
    fun `parse all primitive types`() {
        val def = McpToolDefinition(
            name = "test",
            description = null,
            inputSchema = McpInputSchema(
                properties = mapOf(
                    "s" to McpPropertySchema(type = "string"),
                    "i" to McpPropertySchema(type = "integer"),
                    "f" to McpPropertySchema(type = "number"),
                    "b" to McpPropertySchema(type = "boolean")
                )
            )
        )
        val descriptor = DefaultMcpToolDefinitionParser.parse(def)
        val params = (descriptor.requiredParameters + descriptor.optionalParameters).associateBy { it.name }
        assertTrue(params["s"]!!.type is ToolParameterType.StringType)
        assertTrue(params["i"]!!.type is ToolParameterType.IntegerType)
        assertTrue(params["f"]!!.type is ToolParameterType.FloatType)
        assertTrue(params["b"]!!.type is ToolParameterType.BooleanType)
    }

    @Test
    fun `parse enum type`() {
        val def = McpToolDefinition(
            name = "set_mode",
            description = null,
            inputSchema = McpInputSchema(
                properties = mapOf(
                    "mode" to McpPropertySchema(type = "string", enum = listOf("fast", "slow", "auto"))
                ),
                required = listOf("mode")
            )
        )
        val descriptor = DefaultMcpToolDefinitionParser.parse(def)
        val modeType = descriptor.requiredParameters[0].type
        assertTrue(modeType is ToolParameterType.EnumType)
        assertEquals(listOf("fast", "slow", "auto"), (modeType as ToolParameterType.EnumType).entries)
    }

    @Test
    fun `parse array type`() {
        val def = McpToolDefinition(
            name = "batch",
            description = null,
            inputSchema = McpInputSchema(
                properties = mapOf(
                    "ids" to McpPropertySchema(
                        type = "array",
                        items = McpPropertySchema(type = "integer")
                    )
                )
            )
        )
        val descriptor = DefaultMcpToolDefinitionParser.parse(def)
        val param = descriptor.optionalParameters[0]
        assertTrue(param.type is ToolParameterType.ListType)
        assertTrue((param.type as ToolParameterType.ListType).itemsType is ToolParameterType.IntegerType)
    }

    @Test
    fun `parse nested object type`() {
        val def = McpToolDefinition(
            name = "create_user",
            description = null,
            inputSchema = McpInputSchema(
                properties = mapOf(
                    "user" to McpPropertySchema(
                        type = "object",
                        properties = mapOf(
                            "name" to McpPropertySchema(type = "string", description = "User name"),
                            "age" to McpPropertySchema(type = "integer")
                        )
                    )
                )
            )
        )
        val descriptor = DefaultMcpToolDefinitionParser.parse(def)
        val userType = descriptor.optionalParameters[0].type
        assertTrue(userType is ToolParameterType.ObjectType)
        assertEquals(2, (userType as ToolParameterType.ObjectType).properties.size)
    }

    @Test
    fun `parse tool with no schema`() {
        val def = McpToolDefinition(name = "ping", description = "Ping", inputSchema = null)
        val descriptor = DefaultMcpToolDefinitionParser.parse(def)
        assertEquals("ping", descriptor.name)
        assertTrue(descriptor.requiredParameters.isEmpty())
        assertTrue(descriptor.optionalParameters.isEmpty())
    }

    @Test
    fun `parse unknown type defaults to string`() {
        val def = McpToolDefinition(
            name = "test",
            description = null,
            inputSchema = McpInputSchema(
                properties = mapOf("x" to McpPropertySchema(type = "custom_type"))
            )
        )
        val descriptor = DefaultMcpToolDefinitionParser.parse(def)
        assertTrue(descriptor.optionalParameters[0].type is ToolParameterType.StringType)
    }
}
