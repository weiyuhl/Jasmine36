package com.lhzkml.jasmine.core.agent.tools.mcp

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor

/**
 * MCP (Model Context Protocol) 工具
 * 完整移植 koog 的 McpTool，作为 Agent 框架与 MCP 服务器之间的桥梁。
 *
 * MCP 工具通过 MCP 客户端调用远程 MCP 服务器上的工具，
 * 将参数和结果在 Agent 框架格式和 MCP 格式之间转换。
 *
 * @param client MCP 客户端
 * @param descriptor 工具描述符
 */
class McpTool(
    private val client: McpClient,
    val descriptor: ToolDescriptor
) {
    /**
     * 执行 MCP 工具
     * @param arguments JSON 格式的参数字符串
     * @return 工具执行结果
     */
    suspend fun execute(arguments: String): McpToolResult {
        return client.callTool(descriptor.name, arguments)
    }
}

/**
 * MCP 工具执行结果
 */
data class McpToolResult(
    val content: String,
    val isError: Boolean = false,
    val metadata: Map<String, String>? = null
)

/**
 * MCP 客户端接口
 * 参考 koog 对 io.modelcontextprotocol.kotlin.sdk.client.Client 的使用。
 *
 * 定义与 MCP 服务器通信的基本操作。
 * 不同传输层（stdio、SSE、HTTP）实现此接口。
 */
interface McpClient : AutoCloseable {
    /** 连接到 MCP 服务器 */
    suspend fun connect()

    /** 列出可用工具 */
    suspend fun listTools(): List<McpToolDefinition>

    /** 调用工具 */
    suspend fun callTool(name: String, arguments: String): McpToolResult

    /** 断开连接 */
    override fun close()
}

/**
 * MCP 工具定义（从服务器获取）
 */
data class McpToolDefinition(
    val name: String,
    val description: String?,
    val inputSchema: McpInputSchema?
)

/**
 * MCP 工具输入 Schema
 */
data class McpInputSchema(
    val type: String = "object",
    val properties: Map<String, McpPropertySchema>? = null,
    val required: List<String>? = null
)

/**
 * MCP 属性 Schema
 */
data class McpPropertySchema(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null,
    val items: McpPropertySchema? = null,
    val properties: Map<String, McpPropertySchema>? = null
)
