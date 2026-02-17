package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolResult

/**
 * 工具抽象基类
 * 参考 koog 的 SimpleTool 设计，适配 jasmine 的模型体系
 *
 * 每个工具需要：
 * 1. 提供 descriptor（工具描述，用于发送给 LLM）
 * 2. 实现 execute（接收 JSON 参数字符串，返回结果字符串）
 */
abstract class Tool {
    /** 工具描述符，用于向 LLM 声明此工具 */
    abstract val descriptor: ToolDescriptor

    /** 工具名称（快捷访问） */
    val name: String get() = descriptor.name

    /**
     * 执行工具
     * @param arguments JSON 格式的参数字符串
     * @return 执行结果字符串
     */
    abstract suspend fun execute(arguments: String): String

    /**
     * 执行 ToolCall 并返回 ToolResult
     */
    suspend fun execute(call: ToolCall): ToolResult {
        val content = try {
            execute(call.arguments)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
        return ToolResult(callId = call.id, name = call.name, content = content)
    }
}
