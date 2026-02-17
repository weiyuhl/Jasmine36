package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolResult

/**
 * 工具注册表
 * 参考 koog 的 ToolRegistry，管理可用工具集合
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun registerAll(vararg toolList: Tool) {
        toolList.forEach { register(it) }
    }

    fun findTool(name: String): Tool? = tools[name]

    fun descriptors(): List<ToolDescriptor> = tools.values.map { it.descriptor }

    fun allTools(): List<Tool> = tools.values.toList()

    suspend fun execute(call: ToolCall): ToolResult {
        val tool = findTool(call.name)
            ?: return ToolResult(callId = call.id, name = call.name, content = "Error: Unknown tool '${call.name}'")
        return tool.execute(call)
    }

    suspend fun executeAll(calls: List<ToolCall>): List<ToolResult> {
        return calls.map { execute(it) }
    }

    companion object {
        fun build(block: ToolRegistry.() -> Unit): ToolRegistry {
            return ToolRegistry().apply(block)
        }
    }
}
