package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.StructuredResponse
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import kotlinx.serialization.KSerializer

/**
 * 预定义节点工厂函�?
 * 移植�?koog �?AIAgentNodes.kt，提供开箱即用的节点类型�?
 *
 * 这些函数返回 AgentNodeDelegate，支�?by 委托语法�?
 * ```kotlin
 * val nodeCallLLM by nodeLLMRequest()
 * val nodeExecTool by nodeExecuteTool()
 * ```
 */

// ========== LLM 请求节点 ==========

/**
 * LLM 请求节点 -- 发送用户消息并获取 LLM 响应
 * 移植�?koog �?nodeLLMRequest
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (LLM 响应，可能包�?tool_calls)
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
 * 移植�?koog �?nodeLLMRequestStreaming
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (收集完成�?LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestStreaming(
    name: String? = null
): AgentNodeDelegate<String, ChatResult> {
    return node(name) { message ->
        session.appendPrompt { user(message) }

        val onChunk: suspend (String) -> Unit = storage.get(PredefinedStrategies.KEY_ON_CHUNK) ?: {}
        val onThinking: suspend (String) -> Unit = storage.get(PredefinedStrategies.KEY_ON_THINKING) ?: {}

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
 * 无工�?LLM 请求节点 -- 发送用户消息，不允许工具调�?
 * 移植�?koog �?nodeLLMRequest(allowToolCalls = false)
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (纯文�?LLM 响应)
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
 * 追加 Prompt 节点 -- �?prompt 追加消息，输入直接传递到输出
 * 移植�?koog �?nodeAppendPrompt
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
 * 空操作节�?-- 直接传递输入到输出
 * 移植�?koog �?nodeDoNothing
 */
fun <T> GraphStrategyBuilder<*, *>.nodeDoNothing(
    name: String? = null
): AgentNodeDelegate<T, T> {
    return node(name) { input -> input }
}

// ========== 工具执行节点 ==========

/**
 * 单工具执行节�?-- 执行单个工具调用
 * 移植�?koog �?nodeExecuteTool
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
 * 多工具执行节�?-- 执行多个工具调用（支持并行）
 * 移植�?koog �?nodeExecuteMultipleTools
 *
 * 输入: List<ToolCall> (工具调用请求列表)
 * 输出: List<ReceivedToolResult> (工具执行结果列表)
 *
 * @param parallelTools 是否并行执行工具，默�?false（顺序执行）
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

// ========== 工具结果发送节�?==========

/**
 * 发送单个工具结果节�?-- 将工具结果追加到 prompt 并请�?LLM
 * 移植�?koog �?nodeLLMSendToolResult
 *
 * 输入: ReceivedToolResult (工具执行结果)
 * 输出: ChatResult (LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendToolResult(
    name: String? = null
): AgentNodeDelegate<ReceivedToolResult, ChatResult> {
    return node(name) { result ->
        session.appendPrompt {
            tool { result(result) }
        }
        session.requestLLM()
    }
}

/**
 * 发送多个工具结果节�?-- 将多个工具结果追加到 prompt 并请�?LLM
 * 移植�?koog �?nodeLLMSendMultipleToolResults
 *
 * 输入: List<ReceivedToolResult> (工具执行结果列表)
 * 输出: ChatResult (LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendMultipleToolResults(
    name: String? = null
): AgentNodeDelegate<List<ReceivedToolResult>, ChatResult> {
    return node(name) { results ->
        session.appendPrompt {
            tool {
                results.forEach { result(it) }
            }
        }
        session.requestLLM()
    }
}

/**
 * 流式发送多个工具结果节�?
 *
 * 输入: List<ReceivedToolResult> (工具执行结果列表)
 * 输出: ChatResult (LLM 流式响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendMultipleToolResultsStreaming(
    name: String? = null
): AgentNodeDelegate<List<ReceivedToolResult>, ChatResult> {
    return node(name) { results ->
        session.appendPrompt {
            tool {
                results.forEach { result(it) }
            }
        }

        val onChunk: suspend (String) -> Unit = storage.get(PredefinedStrategies.KEY_ON_CHUNK) ?: {}
        val onThinking: suspend (String) -> Unit = storage.get(PredefinedStrategies.KEY_ON_THINKING) ?: {}

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

// ========== 中优先级预定义节�?==========
// 移植�?koog �?AIAgentNodes.kt

/**
 * 强制只能调用工具�?LLM 请求节点
 * 移植�?koog �?nodeLLMRequestOnlyCallingTools
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (LLM 响应，ToolChoice.Required)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestOnlyCallingTools(
    name: String? = null
): AgentNodeDelegate<String, ChatResult> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLMOnlyCallingTools()
    }
}

/**
 * 强制使用指定工具�?LLM 请求节点
 * 移植�?koog �?nodeLLMRequestForceOneTool
 *
 * 输入: String (用户消息)
 * 输出: ChatResult (LLM 响应，ToolChoice.Named)
 *
 * @param toolName 强制使用的工具名�?
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestForceOneTool(
    name: String? = null,
    toolName: String
): AgentNodeDelegate<String, ChatResult> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLMForceOneTool(toolName)
    }
}

/**
 * 多响�?LLM 请求节点
 * 移植�?koog �?nodeLLMRequestMultiple
 *
 * 输入: String (用户消息)
 * 输出: List<ChatResult> (LLM 响应列表)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestMultiple(
    name: String? = null
): AgentNodeDelegate<String, List<ChatResult>> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLMMultiple()
    }
}

/**
 * 多响�?+ 只能调用工具�?LLM 请求节点
 * 移植�?koog �?nodeLLMRequestMultipleOnlyCallingTools
 *
 * 输入: String (用户消息)
 * 输出: List<ChatResult> (LLM 响应列表，ToolChoice.Required)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestMultipleOnlyCallingTools(
    name: String? = null
): AgentNodeDelegate<String, List<ChatResult>> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLMMultipleOnlyCallingTools()
    }
}

/**
 * 结构化输�?LLM 请求节点
 * 移植�?koog �?nodeLLMRequestStructured
 *
 * 输入: String (用户消息)
 * 输出: Result<StructuredResponse<T>> (结构化响�?
 *
 * @param serializer 目标类型的序列化�?
 * @param examples 可选的示例列表
 */
fun <T> GraphStrategyBuilder<*, *>.nodeLLMRequestStructured(
    name: String? = null,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList()
): AgentNodeDelegate<String, Result<StructuredResponse<T>>> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLMStructured(serializer, examples)
    }
}

/**
 * 历史压缩节点
 * 移植�?koog �?nodeLLMCompressHistory
 *
 * 压缩当前对话历史后透传输入�?
 *
 * 输入: T (任意类型)
 * 输出: T (透传输入)
 *
 * @param strategy 压缩策略，默�?WholeHistory
 * @param preserveMemory 是否保留记忆相关消息，默�?true
 */
fun <T> GraphStrategyBuilder<*, *>.nodeLLMCompressHistory(
    name: String? = null,
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
): AgentNodeDelegate<T, T> {
    return node(name) { input ->
        session.replaceHistoryWithTLDR(strategy, preserveMemory)
        input
    }
}

/**
 * 发送工具结�?+ 强制只能调用工具的节�?
 * 移植�?koog �?nodeLLMSendToolResultOnlyCallingTools
 *
 * 输入: List<ReceivedToolResult> (工具执行结果列表)
 * 输出: ChatResult (LLM 响应，ToolChoice.Required)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendToolResultOnlyCallingTools(
    name: String? = null
): AgentNodeDelegate<List<ReceivedToolResult>, ChatResult> {
    return node(name) { results ->
        session.appendPrompt {
            tool {
                results.forEach { result(it) }
            }
        }
        session.requestLLMOnlyCallingTools()
    }
}

/**
 * 发送多个工具结果并获取多响应的节点
 * 移植�?koog �?nodeLLMSendMultipleToolResults (返回 List<Message.Response> 版本)
 *
 * 输入: List<ReceivedToolResult> (工具执行结果列表)
 * 输出: List<ChatResult> (LLM 多响�?
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendMultipleToolResultsMultiple(
    name: String? = null
): AgentNodeDelegate<List<ReceivedToolResult>, List<ChatResult>> {
    return node(name) { results ->
        session.appendPrompt {
            tool {
                results.forEach { result(it) }
            }
        }
        session.requestLLMMultiple()
    }
}

/**
 * 发送多个工具结�?+ 强制只能调用工具的节�?
 * 移植�?koog �?nodeLLMSendMultipleToolResultsOnlyCallingTools
 *
 * 输入: List<ReceivedToolResult> (工具执行结果列表)
 * 输出: List<ChatResult> (LLM 多响应，ToolChoice.Required)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMSendMultipleToolResultsOnlyCallingTools(
    name: String? = null
): AgentNodeDelegate<List<ReceivedToolResult>, List<ChatResult>> {
    return node(name) { results ->
        session.appendPrompt {
            tool {
                results.forEach { result(it) }
            }
        }
        session.requestLLMMultipleOnlyCallingTools()
    }
}

/**
 * 执行多工具并发送结果给 LLM 的节�?
 * 移植�?koog �?nodeExecuteMultipleToolsAndSendResults
 *
 * 输入: List<ToolCall> (工具调用请求列表)
 * 输出: List<ChatResult> (LLM 多响�?
 *
 * @param parallelTools 是否并行执行工具，默�?false
 */
fun GraphStrategyBuilder<*, *>.nodeExecuteMultipleToolsAndSendResults(
    name: String? = null,
    parallelTools: Boolean = false
): AgentNodeDelegate<List<ToolCall>, List<ChatResult>> {
    return node(name) { toolCalls ->
        val results = if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }
        session.appendPrompt {
            tool {
                results.forEach { result(it) }
            }
        }
        session.requestLLMMultiple()
    }
}

/**
 * 直接调用指定工具的节点（不经�?LLM 选择�?
 * 移植�?koog �?nodeExecuteSingleTool
 *
 * �?nodeExecuteTool 不同，此节点直接按工具名调用，不需�?LLM 生成 tool_call�?
 * 可选将调用过程追加�?prompt（便于后�?LLM 了解上下文）�?
 *
 * 输入: String (工具参数，JSON 格式)
 * 输出: ReceivedToolResult (工具执行结果)
 *
 * @param toolName 要调用的工具名称
 * @param doUpdatePrompt 是否将工具调用信息追加到 prompt，默�?true
 */
fun GraphStrategyBuilder<*, *>.nodeExecuteSingleTool(
    name: String? = null,
    toolName: String,
    doUpdatePrompt: Boolean = true
): AgentNodeDelegate<String, ReceivedToolResult> {
    return node(name) { toolArgs ->
        if (doUpdatePrompt) {
            session.appendPrompt {
                user("Tool call: $toolName was explicitly called with args: $toolArgs")
            }
        }

        val toolCall = ToolCall(
            id = "explicit_${toolName}_${System.currentTimeMillis()}",
            name = toolName,
            arguments = toolArgs
        )
        val result = environment.executeTool(toolCall)

        if (doUpdatePrompt) {
            session.appendPrompt {
                user("Tool call: $toolName was explicitly called and returned result: ${result.content}")
            }
        }

        result
    }
}

/**
 * 流式请求 LLM 并收集结果更�?prompt 的节�?
 * 移植�?koog �?nodeLLMRequestStreamingAndSendResults
 *
 * 流式请求 LLM，通过回调输出 chunk，收集完整响应后自动更新 prompt�?
 * 输入直接透传到输出（透传节点模式）�?
 *
 * 输入: T (任意类型，透传)
 * 输出: List<ChatResult> (收集到的 LLM 响应列表)
 */
fun <T> GraphStrategyBuilder<*, *>.nodeLLMRequestStreamingAndSendResults(
    name: String? = null
): AgentNodeDelegate<T, List<ChatResult>> {
    return node(name) { _ ->
        val onChunk: suspend (String) -> Unit = storage.get(PredefinedStrategies.KEY_ON_CHUNK) ?: {}
        val onThinking: suspend (String) -> Unit = storage.get(PredefinedStrategies.KEY_ON_THINKING) ?: {}

        val streamResult = session.requestLLMStream(onChunk, onThinking)
        val chatResult = ChatResult(
            content = streamResult.content,
            usage = streamResult.usage,
            finishReason = streamResult.finishReason,
            toolCalls = streamResult.toolCalls,
            thinking = streamResult.thinking
        )

        // requestLLMStream 已自动追�?assistant 消息�?prompt
        listOf(chatResult)
    }
}


// ========== Message 类型的预定义节点 ==========
// 移植�?koog 的类型化消息系统

/**
 * LLM 请求节点 -- 返回 Message.Response
 * 移植�?koog �?nodeLLMRequest (Message 版本)
 *
 * 输入: String (用户消息)
 * 输出: Message.Response (类型�?LLM 响应)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestAsMessage(
    name: String? = null
): AgentNodeDelegate<String, com.lhzkml.jasmine.core.prompt.model.Message.Response> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLMAsMessage()
    }
}

/**
 * LLM 请求节点 -- 返回完整�?Message.Response 列表
 * 包含 thinking + assistant + tool calls
 *
 * 输入: String (用户消息)
 * 输出: List<Message.Response> (类型�?LLM 响应列表)
 */
fun GraphStrategyBuilder<*, *>.nodeLLMRequestAsMessages(
    name: String? = null
): AgentNodeDelegate<String, List<com.lhzkml.jasmine.core.prompt.model.Message.Response>> {
    return node(name) { message ->
        session.appendPrompt { user(message) }
        session.requestLLMAsMessages()
    }
}
