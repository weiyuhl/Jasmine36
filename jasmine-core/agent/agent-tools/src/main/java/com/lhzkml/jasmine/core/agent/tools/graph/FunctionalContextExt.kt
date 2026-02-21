package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.StructuredResponse
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import kotlinx.serialization.KSerializer

/**
 * FunctionalContext 扩展函数
 * 移植自 koog 的 AIAgentFunctionalContextExt.kt，
 * 为 AgentGraphContext 提供便捷的 LLM 交互和工具执行方法，
 * 大幅提升 functionalStrategy 的易用性。
 *
 * 使用方式：
 * ```kotlin
 * val strategy = functionalStrategy<String, String>("chat") { input ->
 *     // 直接使用扩展函数，无需手动操作 session
 *     val result = requestLLM(input)
 *     if (result.hasToolCalls) {
 *         val toolResults = executeMultipleTools(result.toolCalls)
 *         val finalResult = sendMultipleToolResults(toolResults)
 *         finalResult.content
 *     } else {
 *         result.content
 *     }
 * }
 * ```
 */

// ========== LLM 请求 ==========

/**
 * 追加用户消息并请求 LLM
 * 移植自 koog 的 AIAgentFunctionalContext.requestLLM
 *
 * @param message 用户消息内容
 * @param allowToolCalls 是否允许工具调用，默认 true
 * @return LLM 响应结果
 */
suspend fun AgentGraphContext.requestLLM(
    message: String,
    allowToolCalls: Boolean = true
): ChatResult {
    session.appendPrompt { user(message) }
    return if (allowToolCalls) {
        session.requestLLM()
    } else {
        session.requestLLMWithoutTools()
    }
}

/**
 * 追加用户消息并流式请求 LLM
 *
 * @param message 用户消息内容
 * @param onChunk 流式内容回调
 * @param onThinking 思考内容回调
 * @return LLM 流式响应结果（转为 ChatResult）
 */
suspend fun AgentGraphContext.requestLLMStream(
    message: String,
    onChunk: suspend (String) -> Unit,
    onThinking: suspend (String) -> Unit = {}
): ChatResult {
    session.appendPrompt { user(message) }
    val streamResult = session.requestLLMStream(onChunk, onThinking)
    return ChatResult(
        content = streamResult.content,
        usage = streamResult.usage,
        finishReason = streamResult.finishReason,
        toolCalls = streamResult.toolCalls,
        thinking = streamResult.thinking
    )
}

/**
 * 追加用户消息并请求 LLM 返回结构化 JSON 输出
 * 移植自 koog 的 AIAgentFunctionalContext.requestLLMStructured
 *
 * @param message 用户消息内容
 * @param serializer 目标类型的序列化器
 * @param examples 可选的示例列表
 * @return Result 包含解析后的 StructuredResponse 或错误
 */
suspend fun <T> AgentGraphContext.requestLLMStructured(
    message: String,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList()
): Result<StructuredResponse<T>> {
    session.appendPrompt { user(message) }
    return session.requestLLMStructured(serializer, examples)
}

/**
 * 追加用户消息并请求 LLM 返回结构化 JSON 输出（inline reified 版本）
 */
suspend inline fun <reified T> AgentGraphContext.requestLLMStructured(
    message: String,
    examples: List<T> = emptyList()
): Result<StructuredResponse<T>> {
    session.appendPrompt { user(message) }
    return session.requestLLMStructured<T>(examples)
}

/**
 * 追加用户消息并强制 LLM 使用指定工具
 * 移植自 koog 的 AIAgentFunctionalContext.requestLLMForceOneTool
 *
 * @param message 用户消息内容
 * @param toolName 强制使用的工具名称
 * @return LLM 响应结果
 */
suspend fun AgentGraphContext.requestLLMForceOneTool(
    message: String,
    toolName: String
): ChatResult {
    session.appendPrompt { user(message) }
    return session.requestLLMForceOneTool(toolName)
}

/**
 * 追加用户消息并强制 LLM 只能调用工具（不能生成文本）
 * 移植自 koog 的 AIAgentFunctionalContext.requestLLMOnlyCallingTools
 *
 * @param message 用户消息内容
 * @return LLM 响应结果
 */
suspend fun AgentGraphContext.requestLLMOnlyCallingTools(
    message: String
): ChatResult {
    session.appendPrompt { user(message) }
    return session.requestLLMOnlyCallingTools()
}

// ========== 响应判断 ==========

/**
 * 如果 LLM 响应是纯文本（无工具调用），执行 action
 * 移植自 koog 的 AIAgentFunctionalContext.onAssistantMessage
 *
 * @param result LLM 响应
 * @param action 当响应为纯文本时执行的操作
 */
inline fun AgentGraphContext.onAssistantMessage(
    result: ChatResult,
    action: (ChatResult) -> Unit
) {
    if (!result.hasToolCalls) {
        action(result)
    }
}

/**
 * 如果 LLM 响应包含工具调用，执行 action
 *
 * @param result LLM 响应
 * @param action 当响应包含工具调用时执行的操作
 */
inline fun AgentGraphContext.onToolCalls(
    result: ChatResult,
    action: (List<ToolCall>) -> Unit
) {
    if (result.hasToolCalls) {
        action(result.toolCalls)
    }
}

// ========== 工具执行 ==========

/**
 * 执行单个工具调用
 * 移植自 koog 的 AIAgentFunctionalContext.executeTool
 *
 * @param toolCall 工具调用请求
 * @return 工具执行结果
 */
suspend fun AgentGraphContext.executeTool(
    toolCall: ToolCall
): ReceivedToolResult {
    return environment.executeTool(toolCall)
}

/**
 * 执行多个工具调用
 * 移植自 koog 的 AIAgentFunctionalContext.executeMultipleTools
 *
 * @param toolCalls 工具调用请求列表
 * @param parallelTools 是否并行执行，默认 false
 * @return 工具执行结果列表
 */
suspend fun AgentGraphContext.executeMultipleTools(
    toolCalls: List<ToolCall>,
    parallelTools: Boolean = false
): List<ReceivedToolResult> {
    return if (parallelTools) {
        environment.executeTools(toolCalls)
    } else {
        toolCalls.map { environment.executeTool(it) }
    }
}

// ========== 工具结果发送 ==========

/**
 * 发送单个工具结果并请求 LLM
 * 移植自 koog 的 AIAgentFunctionalContext.sendToolResult
 *
 * @param toolResult 工具执行结果
 * @return LLM 响应结果
 */
suspend fun AgentGraphContext.sendToolResult(
    toolResult: ReceivedToolResult
): ChatResult {
    session.appendPrompt {
        message(ChatMessage.toolResult(
            com.lhzkml.jasmine.core.prompt.model.ToolResult(
                callId = toolResult.id,
                name = toolResult.tool,
                content = toolResult.content
            )
        ))
    }
    return session.requestLLM()
}

/**
 * 发送多个工具结果并请求 LLM
 * 移植自 koog 的 AIAgentFunctionalContext.sendMultipleToolResults
 *
 * @param results 工具执行结果列表
 * @return LLM 响应结果
 */
suspend fun AgentGraphContext.sendMultipleToolResults(
    results: List<ReceivedToolResult>
): ChatResult {
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
    return session.requestLLM()
}

// ========== 历史压缩 ==========

/**
 * 压缩当前对话历史
 * 移植自 koog 的 AIAgentFunctionalContext.compressHistory
 *
 * @param strategy 压缩策略，默认 WholeHistory
 */
suspend fun AgentGraphContext.compressHistory(
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory
) {
    session.replaceHistoryWithTLDR(strategy)
}

// ========== Token 用量 ==========

/**
 * 获取当前 prompt 的估算 token 数
 *
 * @return 估算的 token 数
 */
fun AgentGraphContext.estimateTokenUsage(): Int {
    return session.prompt.messages.sumOf { msg ->
        // 粗略估算: 每4个字符约1个token
        (msg.content.length / 4) + 4
    }
}

// ========== 中优先级扩展函数 ==========
// 移植自 koog 的 AIAgentFunctionalContextExt.kt

/**
 * 追加用户消息并请求 LLM 返回多个响应
 * 移植自 koog 的 AIAgentFunctionalContext.requestLLMMultiple
 *
 * @param message 用户消息内容
 * @return LLM 响应结果列表
 */
suspend fun AgentGraphContext.requestLLMMultiple(
    message: String
): List<ChatResult> {
    session.appendPrompt { user(message) }
    return session.requestLLMMultiple()
}

/**
 * 如果 LLM 响应列表包含工具调用，执行 action
 * 移植自 koog 的 AIAgentFunctionalContext.onMultipleToolCalls
 *
 * @param results LLM 响应列表
 * @param action 当响应包含工具调用时执行的操作
 */
inline fun AgentGraphContext.onMultipleToolCalls(
    results: List<ChatResult>,
    action: (List<ChatResult>) -> Unit
) {
    val toolCallResults = results.filter { it.hasToolCalls }
    if (toolCallResults.isNotEmpty()) {
        action(toolCallResults)
    }
}

/**
 * 如果 LLM 响应列表包含纯助手消息，执行 action
 * 移植自 koog 的 AIAgentFunctionalContext.onMultipleAssistantMessages
 *
 * @param results LLM 响应列表
 * @param action 当响应包含纯助手消息时执行的操作
 */
inline fun AgentGraphContext.onMultipleAssistantMessages(
    results: List<ChatResult>,
    action: (List<ChatResult>) -> Unit
) {
    val assistantResults = results.filter { !it.hasToolCalls }
    if (assistantResults.isNotEmpty()) {
        action(assistantResults)
    }
}

/**
 * 检查响应列表是否包含工具调用
 * 移植自 koog 的 containsToolCalls
 *
 * @param results LLM 响应列表
 * @return 是否包含工具调用
 */
fun containsToolCalls(results: List<ChatResult>): Boolean {
    return results.any { it.hasToolCalls }
}

/**
 * 从响应列表中提取所有工具调用
 * 移植自 koog 的 extractToolCalls
 *
 * @param results LLM 响应列表
 * @return 所有工具调用的列表
 */
fun extractToolCalls(results: List<ChatResult>): List<ToolCall> {
    return results.flatMap { it.toolCalls }
}

/**
 * 发送多个工具结果并获取多响应
 *
 * @param results 工具执行结果列表
 * @return LLM 多响应结果列表
 */
suspend fun AgentGraphContext.sendMultipleToolResultsMultiple(
    results: List<ReceivedToolResult>
): List<ChatResult> {
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
    return session.requestLLMMultiple()
}
