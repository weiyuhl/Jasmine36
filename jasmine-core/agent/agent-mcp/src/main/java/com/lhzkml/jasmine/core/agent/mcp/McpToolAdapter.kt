package com.lhzkml.jasmine.core.agent.mcp

import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor

/**
 * MCP 工具适配�?
 * �?McpTool 适配为标�?Tool 抽象类，使其可以注册�?ToolRegistry�?
 */
class McpToolAdapter(
    private val mcpTool: McpTool
) : Tool() {
    override val descriptor: ToolDescriptor get() = mcpTool.descriptor

    override suspend fun execute(arguments: String): String {
        val mcpResult = mcpTool.execute(arguments)
        return if (mcpResult.isError) {
            "Error: ${mcpResult.content}"
        } else {
            mcpResult.content
        }
    }
}

/**
 * �?McpToolRegistry 中的所有工具注册到标准 ToolRegistry
 */
fun com.lhzkml.jasmine.core.agent.tools.ToolRegistry.registerMcpTools(mcpRegistry: McpToolRegistry) {
    for (descriptor in mcpRegistry.descriptors()) {
        val mcpTool = mcpRegistry.findTool(descriptor.name) ?: continue
        register(McpToolAdapter(mcpTool))
    }
}
