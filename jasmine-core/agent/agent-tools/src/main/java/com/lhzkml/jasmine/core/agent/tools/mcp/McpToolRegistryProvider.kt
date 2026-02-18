package com.lhzkml.jasmine.core.agent.tools.mcp

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor

/**
 * MCP 工具注册表提供者
 * 完整移植 koog 的 McpToolRegistryProvider，
 * 从 MCP 服务器获取工具并注册到 ToolRegistry。
 *
 * 使用方式：
 * ```kotlin
 * val client = HttpMcpClient("http://localhost:8080/mcp")
 * client.connect()
 * val registry = McpToolRegistryProvider.fromClient(client)
 * ```
 */
object McpToolRegistryProvider {

    /**
     * 从已连接的 MCP 客户端创建 ToolRegistry
     *
     * @param mcpClient 已连接的 MCP 客户端
     * @param parser 工具定义解析器
     * @return 包含所有 MCP 工具描述符的 ToolRegistry
     */
    suspend fun fromClient(
        mcpClient: McpClient,
        parser: McpToolDefinitionParser = DefaultMcpToolDefinitionParser
    ): McpToolRegistry {
        val definitions = mcpClient.listTools()
        val tools = mutableListOf<McpTool>()
        val descriptors = mutableListOf<ToolDescriptor>()

        for (definition in definitions) {
            try {
                val descriptor = parser.parse(definition)
                tools.add(McpTool(mcpClient, descriptor))
                descriptors.add(descriptor)
            } catch (e: Exception) {
                // 跳过解析失败的工具，记录错误
                System.err.println("Failed to parse MCP tool: ${definition.name}: ${e.message}")
            }
        }

        return McpToolRegistry(tools, descriptors)
    }
}

/**
 * MCP 工具注册表
 * 包含从 MCP 服务器获取的工具，支持按名称查找和执行。
 */
class McpToolRegistry(
    private val tools: List<McpTool>,
    private val _descriptors: List<ToolDescriptor>
) {
    /** 获取所有工具描述符 */
    fun descriptors(): List<ToolDescriptor> = _descriptors

    /** 按名称查找工具 */
    fun findTool(name: String): McpTool? =
        tools.find { it.descriptor.name == name }

    /** 执行工具 */
    suspend fun execute(name: String, arguments: String): McpToolResult {
        val tool = findTool(name)
            ?: throw IllegalArgumentException("MCP tool not found: $name")
        return tool.execute(arguments)
    }

    /** 工具数量 */
    val size: Int get() = tools.size
}
