package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ToolCall

/**
 * 预定义节点工厂函数
 * 移植自 koog 的 AIAgentNodes.kt，提供开箱即用的节点类型。
 *
 * 这些函数返回 AgentNodeDelegate，支持 by 委托语法：
 * ```kotlin
 * val nodeCallLLM by nodeLLMRequest()
 * val nodeExecTool by nodeExecuteTool()
 * ```
 */

// ========== LLM 请求节点 ==========

/**
 * LLM 请求节点 -- 发送用户消息并获取 LLM 响应
 * 移植自 koog 的 nodeLLMRequest
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (LLM 响应，可能包含 tool_calls)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequest(
    name: String? = null
): AgentNodeDelegate<String, ChatResult> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLM()
    }
}

/**
 * LLM 流式请求节点 -- 发送用户消息并流式获取 LLM 响应
 * 移植自 koog 的 nodeLLMRequestStreaming
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (收集完成的 LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestStreaming(
    name: String? = null
): AgentNodeDelegate<String, ChatResult> {
    return node(name) { message ->
        session.appendPrompt { user(message) }

        @Suppress("UNCHECKED_CAST")
        val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
        @Suppress("UNCHECKED_CAST")
        val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }

        val streamResult = session.requestLLMStream(onChunk, onThinking)
        ChatResult(
            content = streamResult.content,
            usage = streamResult.usage,
            finishReason = streamResult.finishReason,
            toolCalls = streamResult.toolCalls,
            thinking = streamResult.thinking
        )
    }
}

/**
 * 无工具 LLM 请求节点 -- 发送用户消息，不允许工具调用
 * 移植自 koog 的 nodeLLMRequest(allowToolCalls = false)
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (纯文本 LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestWithoutTools(
    name: String? = null
): AgentNodeDelegate<String, ChatResult> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        // 临时清空工具列表，请求后恢复
        val originalTools = session.tools
        session.tools = emptyList()
        val result = session.requestLLM()
        session.tools = originalTools
        result
    }
}

/**
 * 追加 Prompt 节点 -- 向 prompt 追加消息，输入直接传递到输出
 * 移植自 koog 的 nodeAppendPrompt
 */
fun <T> GraphStrategyBuilder<*, *>.nodeAppendPrompt(
    name: String? = null,
    body: suspend AgentGraphContext.() -> ChatMessage
): AgentNodeDelegate<T, T> {
    return node(name) { input ->
        val message = body()
        session.appendPrompt { message(message) }
        input
    }
}

/**
 * 空操作节点 -- 直接传递输入到输出
 * 移植自 koog 的 nodeDoNothing
 */
fun <T> GraphStrategyBuilder<*, *>.nodeDoNothing(
    name: String? = null
): AgentNodeDelegate<T, T> {
    return node(name) { input -> input }
}

// ========== 工具执行节点 ==========

/**
 * 单工具执行节点 -- 执行单个工具调用
 * 移植自 koog 的 nodeExecuteTool
 *
 * 输入: ToolCall (工具调用请求)
 * 输出: ReceivedToolResult (工具执行结果)
 */
fun GraphStrategyBuilder<*, *>.nodeExecuteTool(
    name: String? = null
): AgentNodeDelegate<ToolCall, ReceivedToolResult> {
    return node(name) { toolCall ->
        environment.executeTool(toolCall)
    }
}

/**
 * 多工具执行节点 -- 执行多个工具调用（支持并行）
 * 移植自 koog 的 nodeExecuteMultipleTools
 *
 * 输入: List<ToolCall> (工具调用请求列表)
 * 输出: List<ReceivedToolResult> (工具执行结果列表)
 *
 * @param parallelTools 是否并行执行工具，默认 false（顺序执行）
 */
fun GraphStrategyBuilder<*, *>.nodeExecuteMultipleTools(
    name: String? = null,
    parallelTools: Boolean = false
): AgentNodeDelegate<List<ToolCall>, List<ReceivedToolResult>> {
    return node(name) { toolCalls ->
        if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }
    }
}

// ========== 工具结果发送节点 ==========

/**
 * 发送单个工具结果节点 -- 将工具结果追加到 prompt 并请求 LLM
 * 移植自 koog 的 nodeLLMSendToolResult
 *
 * 输入: ReceivedToolResult (工具执行结果)
 * 输出: ChatResult (LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendToolResult(
    name: String? = null
): AgentNodeDelegate<ReceivedToolResult, ChatResult> {
    return node(name) { result ->
        session.appendPrompt {
            message(ChatMessage.toolResult(
                com.lhzkml.jasmine.core.prompt.model.ToolResult(
                    callId = result.id,
                    name = result.tool,
                    content = result.content
                )
            ))
        }
        session.requestLLM()
    }
}

/**
 * 发送多个工具结果节点 -- 将多个工具结果追加到 prompt 并请求 LLM
 * 移植自 koog 的 nodeLLMSendMultipleToolResults
 *
 * 输入: List<ReceivedToolResult> (工具执行结果列表)
 * 输出: ChatResult (LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendMultipleToolResults(
    name: String? = null
): AgentNodeDelegate<List<ReceivedToolResult>, ChatResult> {
    return node(name) { results ->
        for (result in results) {
            session.appendPrompt {
                message(ChatMessage.toolResult(
                    com.lhzkml.jasmine.core.prompt.model.ToolResult(
                        callId = result.id,
                        name = result.tool,
                        content = result.content
                    )
                ))
            }
        }
        session.requestLLM()
    }
}

/**
 * 流式发送多个工具结果节点
 *
 * 输入: List<ReceivedToolResult> (工具执行结果列表)
 * 输出: ChatResult (LLM 流式响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendMultipleToolResultsStreaming(
    name: String? = null
): AgentNodeDelegate<List<ReceivedToolResult>, ChatResult> {
    return node(name) { results ->
        for (result in results) {
            session.appendPrompt {
                message(ChatMessage.toolResult(
                    com.lhzkml.jasmine.core.prompt.model.ToolResult(
                        callId = result.id,
                        name = result.tool,
                        content = result.content
                    )
                ))
            }
        }

        @Suppress("UNCHECKED_CAST")
        val onChunk = get<suspend (String) -> Unit>("onChunk") ?: { _ -> }
        @Suppress("UNCHECKED_CAST")
        val onThinking = get<suspend (String) -> Unit>("onThinking") ?: { _ -> }

        val streamResult = session.requestLLMStream(onChunk, onThinking)
        ChatResult(
            content = streamResult.content,
            usage = streamResult.usage,
            finishReason = streamResult.finishReason,
            toolCalls = streamResult.toolCalls,
            thinking = streamResult.thinking
        )
    }
}
