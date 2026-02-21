package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Agent 环境接口
 * 移植自 koog 的 AIAgentEnvironment，负责工具执行和问题报告。
 */
interface AgentEnvironment {
    /** 执行单个工具调用 */
    suspend fun executeTool(toolCall: ToolCall): ReceivedToolResult

    /** 并行执行多个工具调用 */
    suspend fun executeTools(toolCalls: List<ToolCall>): List<ReceivedToolResult>

    /** 报告问题 */
    suspend fun reportProblem(exception: Throwable)
}

/**
 * 通用 Agent 环境实现
 * 移植自 koog 的 GenericAgentEnvironment，
 * 使用 ToolRegistry 执行工具，并对结果进行分类（Success/Failure/ValidationError）。
 */
class GenericAgentEnvironment(
    private val agentId: String,
    private val toolRegistry: ToolRegistry
) : AgentEnvironment {

    override suspend fun executeTool(toolCall: ToolCall): ReceivedToolResult {
        val tool = toolRegistry.findTool(toolCall.name)
            ?: return ReceivedToolResult(
                id = toolCall.id,
                tool = toolCall.name,
                toolArgs = toolCall.arguments,
                toolDescription = null,
                content = "Tool with name '${toolCall.name}' not found in the tool registry. Use one of the available tools.",
                resultKind = ToolResultKind.Failure(null)
            )

        val toolDescription = tool.descriptor.description

        return try {
            val content = tool.execute(toolCall.arguments)
            ReceivedToolResult(
                id = toolCall.id,
                tool = toolCall.name,
                toolArgs = toolCall.arguments,
                toolDescription = toolDescription,
                content = content,
                resultKind = ToolResultKind.Success
            )
        } catch (e: ToolValidationException) {
            ReceivedToolResult(
                id = toolCall.id,
                tool = toolCall.name,
                toolArgs = toolCall.arguments,
                toolDescription = toolDescription,
                content = e.message ?: "Validation error",
                resultKind = ToolResultKind.ValidationError(e)
            )
        } catch (e: Exception) {
            ReceivedToolResult(
                id = toolCall.id,
                tool = toolCall.name,
                toolArgs = toolCall.arguments,
                toolDescription = toolDescription,
                content = "Tool with name '${toolCall.name}' failed to execute due to the error: ${e.message}",
                resultKind = ToolResultKind.Failure(e)
            )
        }
    }

    override suspend fun executeTools(toolCalls: List<ToolCall>): List<ReceivedToolResult> {
        return supervisorScope {
            toolCalls.map { call ->
                async { executeTool(call) }
            }.awaitAll()
        }
    }

    override suspend fun reportProblem(exception: Throwable) {
        throw exception
    }
}

/**
 * 工具验证异常
 * 移植自 koog 的 ToolException，
 * 当工具参数验证失败时抛出，LLM 可以根据错误信息重试。
 */
class ToolValidationException(message: String) : Exception(message)
