package com.lhzkml.jasmine.core.agent.tools.mcp

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor

/**
 * MCP 工具注册表提供者
 * 完整移植 koog 的 McpToolRegistryProvider，
 * 从 MCP 服务器获取工具并注册到 ToolRegistry。
 *
 * 使用方式：
 * ```kotlin
 * // 方式1：从已连接的客户端
 * val client = HttpMcpClient("http://localhost:8080/mcp")
 * client.connect()
 * val registry = McpToolRegistryProvider.fromClient(client)
 *
 * // 方式2：从传输层（自动创建客户端并连接）
 * val transport = SseMcpTransport("http://localhost:8080/mcp")
 * val registry = McpToolRegistryProvider.fromTransport(transport)
 * ```
 */
object McpToolRegistryProvider {

    /** MCP 客户端默认名称 */
    const val DEFAULT_MCP_CLIENT_NAME: String = "mcp-client-cli"

    /** MCP 客户端默认版本 */
    const val DEFAULT_MCP_CLIENT_VERSION: String = "1.0.0"

    /**
     * 从已连接的 MCP 客户端创建 McpToolRegistry
     *
     * @param mcpClient 已连接的 MCP 客户端
     * @param parser 工具定义解析器
     * @return 包含所有 MCP 工具描述符的 McpToolRegistry
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
                System.err.println("Failed to parse MCP tool: ${definition.name}: ${e.message}")
            }
        }

        return McpToolRegistry(tools, descriptors)
    }

    /**
     * 从 MCP 传输层创建 McpToolRegistry
     * 完整移植 koog 的 fromTransport
     *
     * 通过提供的传输层建立与 MCP 服务器的连接。
     * 通常在 MCP 服务器作为独立进程运行时使用（如 Docker 容器或 CLI 工具）。
     *
     * @param transport 传输层实现
     * @param parser 工具定义解析器
     * @param name MCP 客户端名称
     * @param version MCP 客户端版本
     * @return 包含所有 MCP 工具描述符的 McpToolRegistry
     */
    suspend fun fromTransport(
        transport: McpTransport,
        parser: McpToolDefinitionParser = DefaultMcpToolDefinitionParser,
        name: String = DEFAULT_MCP_CLIENT_NAME,
        version: String = DEFAULT_MCP_CLIENT_VERSION
    ): McpToolRegistry {
        // 从传输层创建客户端
        val mcpClient = transport.createClient(name, version)

        // 连接到 MCP 服务器
        mcpClient.connect()

        return fromClient(mcpClient, parser)
    }
}

/**
 * MCP 传输层接口
 * 完整移植 koog 对 Transport 的使用
 *
 * 定义创建 MCP 客户端的传输层抽象。
 * 不同传输层（stdio、SSE、HTTP）实现此接口。
 */
interface McpTransport {
    /**
     * 从此传输层创建 MCP 客户端
     *
     * @param name 客户端名称
     * @param version 客户端版本
     * @return MCP 客户端实例
     */
    fun createClient(name: String, version: String): McpClient
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
