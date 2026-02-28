package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.StructuredResponse
import com.lhzkml.jasmine.core.prompt.llm.replaceHistoryWithTLDR
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.latestTokenUsage
import kotlinx.serialization.KSerializer

/**
 * FunctionalContext 扩展函数
 * 移植�?koog �?AIAgentFunctionalContextExt.kt�?
 * �?AgentGraphContext 提供便捷�?LLM 交互和工具执行方法，
 * 大幅提升 functionalStrategy 的易用性�?
 *
 * 使用方式�?
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
 * 追加用户消息并请�?LLM
 * 移植�?koog �?AIAgentFunctionalContext.requestLLM
 *
 * @param message 用户消息内容
 * @param allowToolCalls 是否允许工具调用，默�?true
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
 * 追加用户消息并流式请�?LLM
 *
 * @param message 用户消息内容
 * @param onChunk 流式内容回调
 * @param onThinking 思考内容回�?
 * @return LLM 流式响应结果（转�?ChatResult�?
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
 * 追加用户消息并请�?LLM 返回结构�?JSON 输出
 * 移植�?koog �?AIAgentFunctionalContext.requestLLMStructured
 *
 * @param message 用户消息内容
 * @param serializer 目标类型的序列化�?
 * @param examples 可选的示例列表
 * @return Result 包含解析后的 StructuredResponse 或错�?
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
 * 追加用户消息并请�?LLM 返回结构�?JSON 输出（inline reified 版本�?
 */
suspend inline fun <reified T> AgentGraphContext.requestLLMStructured(
    message: String,
    examples: List<T> = emptyList()
): Result<StructuredResponse<T>> {
    session.appendPrompt { user(message) }
    return session.requestLLMStructured<T>(examples)
}

/**
 * 追加用户消息并强�?LLM 使用指定工具
 * 移植�?koog �?AIAgentFunctionalContext.requestLLMForceOneTool
 *
 * @param message 用户消息内容
 * @param toolName 强制使用的工具名�?
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
 * 追加用户消息并强�?LLM 只能调用工具（不能生成文本）
 * 移植�?koog �?AIAgentFunctionalContext.requestLLMOnlyCallingTools
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
 * 移植�?koog �?AIAgentFunctionalContext.onAssistantMessage
 *
 * @param result LLM 响应
 * @param action 当响应为纯文本时执行的操�?
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
 * 如果 LLM 响应包含工具调用，执�?action
 *
 * @param result LLM 响应
 * @param action 当响应包含工具调用时执行的操�?
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
 * 移植�?koog �?AIAgentFunctionalContext.executeTool
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
 * 移植�?koog �?AIAgentFunctionalContext.executeMultipleTools
 *
 * @param toolCalls 工具调用请求列表
 * @param parallelTools 是否并行执行，默�?false
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

// ========== 工具结果发�?==========

/**
 * 发送单个工具结果并请求 LLM
 * 移植�?koog �?AIAgentFunctionalContext.sendToolResult
 *
 * @param toolResult 工具执行结果
 * @return LLM 响应结果
 */
suspend fun AgentGraphContext.sendToolResult(
    toolResult: ReceivedToolResult
): ChatResult {
    session.appendPrompt {
        tool {
            result(toolResult)
        }
    }
    return session.requestLLM()
}

/**
 * 发送多个工具结果并请求 LLM
 * 移植�?koog �?AIAgentFunctionalContext.sendMultipleToolResults
 *
 * 注意: koog 原版返回 List<Message.Response>（调�?requestLLMMultiple），
 * jasmine 适配为返�?List<ChatResult>�?
 *
 * @param results 工具执行结果列表
 * @return LLM 响应结果列表
 */
suspend fun AgentGraphContext.sendMultipleToolResults(
    results: List<ReceivedToolResult>
): List<ChatResult> {
    session.appendPrompt {
        tool {
            results.forEach { result(it) }
        }
    }
    return session.requestLLMMultiple()
}

// ========== 历史压缩 ==========

/**
 * 压缩当前对话历史
 * 移植�?koog �?AIAgentFunctionalContext.compressHistory
 *
 * @param strategy 压缩策略，默�?WholeHistory
 * @param preserveMemory 是否保留记忆相关消息，默�?true
 */
suspend fun AgentGraphContext.compressHistory(
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
) {
    session.replaceHistoryWithTLDR(strategy, preserveMemory)
}

// ========== Token 用量 ==========

/**
 * 获取当前 prompt 的估�?token �?
 *
 * @return 估算�?token �?
 */
fun AgentGraphContext.estimateTokenUsage(): Int {
    return session.prompt.messages.sumOf { msg ->
        // 粗略估算: �?个字符约1个token
        (msg.content.length / 4) + 4
    }
}

// ========== 中优先级扩展函数 ==========
// 移植�?koog �?AIAgentFunctionalContextExt.kt

/**
 * 追加用户消息并请�?LLM 返回多个响应
 * 移植�?koog �?AIAgentFunctionalContext.requestLLMMultiple
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
 * 如果 LLM 响应列表包含工具调用，执�?action
 * 移植�?koog �?AIAgentFunctionalContext.onMultipleToolCalls
 *
 * @param results LLM 响应列表
 * @param action 当响应包含工具调用时执行的操�?
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
 * 移植�?koog �?AIAgentFunctionalContext.onMultipleAssistantMessages
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
 * 检查响应列表是否包含工具调�?
 * 移植�?koog �?containsToolCalls
 *
 * @param results LLM 响应列表
 * @return 是否包含工具调用
 */
fun containsToolCalls(results: List<ChatResult>): Boolean {
    return results.any { it.hasToolCalls }
}

/**
 * 从响应列表中提取所有工具调�?
 * 移植�?koog �?extractToolCalls
 *
 * @param results LLM 响应列表
 * @return 所有工具调用的列表
 */
fun extractToolCalls(results: List<ChatResult>): List<ToolCall> {
    return results.flatMap { it.toolCalls }
}

/**
 * 发送多个工具结果并获取多响�?
 *
 * @param results 工具执行结果列表
 * @return LLM 多响应结果列�?
 */
suspend fun AgentGraphContext.sendMultipleToolResultsMultiple(
    results: List<ReceivedToolResult>
): List<ChatResult> {
    session.appendPrompt {
        tool {
            results.forEach { result(it) }
        }
    }
    return session.requestLLMMultiple()
}

// ========== 响应类型判断 ==========
// 移植�?koog �?asAssistantMessageOrNull / asAssistantMessage
// koog 用密封类层次(Message.Assistant/Tool.Call/Reasoning)，jasmine 用扁�?ChatResult�?
// 适配为判�?ChatResult 是否为纯助手消息（无工具调用）�?

/**
 * 如果 ChatResult 是纯助手消息（无工具调用），返回自身；否则返�?null
 * 移植�?koog �?Message.Response.asAssistantMessageOrNull()
 *
 * @return ChatResult �?null
 */
fun ChatResult.asAssistantMessageOrNull(): ChatResult? {
    return if (!hasToolCalls) this else null
}

/**
 * 强制�?ChatResult 视为纯助手消�?
 * 移植�?koog �?Message.Response.asAssistantMessage()
 *
 * @return ChatResult
 * @throws IllegalStateException 如果包含工具调用
 */
fun ChatResult.asAssistantMessage(): ChatResult {
    check(!hasToolCalls) { "ChatResult contains tool calls, not a pure assistant message" }
    return this
}

// ========== 直接工具调用 ==========

/**
 * 直接调用指定工具（不经过 LLM 选择�?
 * 移植�?koog �?AIAgentFunctionalContext.executeSingleTool
 *
 * koog 原版使用 SafeTool<ToolArg, TResult> 类型化参数，jasmine 适配为工具名 + JSON 参数字符串�?
 *
 * @param toolName 要调用的工具名称
 * @param toolArgs 工具参数（JSON 格式字符串）
 * @param doAppendPrompt 是否将工具调用信息追加到 prompt，默�?true
 * @return 工具执行结果
 */
suspend fun AgentGraphContext.executeSingleTool(
    toolName: String,
    toolArgs: String,
    doAppendPrompt: Boolean = true
): ReceivedToolResult {
    if (doAppendPrompt) {
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

    if (doAppendPrompt) {
        session.appendPrompt {
            user("Tool call: $toolName was explicitly called and returned result: ${result.content}")
        }
    }

    return result
}

// ========== Token 用量（实际值） ==========

/**
 * 获取最新的 token 用量
 * 移植�?koog �?AIAgentFunctionalContext.latestTokenUsage
 *
 * koog �?prompt.latestTokenUsage 读取实际值，jasmine 从最近一�?ChatResult �?usage 获取�?
 *
 * @param lastResult 最近一�?LLM 响应（可选）
 * @return token 用量，如果无法获取返�?null
 */
fun AgentGraphContext.latestTokenUsage(lastResult: ChatResult? = null): Int? {
    return lastResult?.usage?.totalTokens
}


// ========== Message 类型的扩展函�?==========
// 移植�?koog 的类型化消息系统

/**
 * 追加用户消息并请�?LLM，返�?Message.Response
 */
suspend fun AgentGraphContext.requestLLMAsMessage(
    message: String
): com.lhzkml.jasmine.core.prompt.model.Message.Response {
    session.appendPrompt { user(message) }
    return session.requestLLMAsMessage()
}

/**
 * 追加用户消息并请�?LLM（不带工具），返�?Message.Response
 */
suspend fun AgentGraphContext.requestLLMWithoutToolsAsMessage(
    message: String
): com.lhzkml.jasmine.core.prompt.model.Message.Response {
    session.appendPrompt { user(message) }
    return session.requestLLMWithoutToolsAsMessage()
}

/**
 * 追加 Message 类型的消息到 prompt
 */
fun AgentGraphContext.appendMessage(message: com.lhzkml.jasmine.core.prompt.model.Message) {
    session.appendMessage(message)
}

/**
 * 批量追加 Message 类型的消息到 prompt
 */
fun AgentGraphContext.appendMessages(messages: List<com.lhzkml.jasmine.core.prompt.model.Message>) {
    session.appendMessages(messages)
}

// ========== Message.Response 版本的响应判�?==========
// 移植�?koog �?AIAgentFunctionalContextExt.kt (Message.Response 版本)

/**
 * 如果 Message.Response �?Assistant 消息，执�?action
 * 移植�?koog �?onAssistantMessage(Message.Response, (Message.Assistant) -> Unit)
 */
inline fun AgentGraphContext.onAssistantMessage(
    response: com.lhzkml.jasmine.core.prompt.model.Message.Response,
    action: (com.lhzkml.jasmine.core.prompt.model.Message.Assistant) -> Unit
) {
    if (response is com.lhzkml.jasmine.core.prompt.model.Message.Assistant) {
        action(response)
    }
}

/**
 * 如果 Message.Response 列表包含 Tool.Call，执�?action
 * 移植�?koog �?onMultipleToolCalls(List<Message.Response>, (List<Message.Tool.Call>) -> Unit)
 */
inline fun AgentGraphContext.onMultipleToolCallMessages(
    responses: List<com.lhzkml.jasmine.core.prompt.model.Message.Response>,
    action: (List<com.lhzkml.jasmine.core.prompt.model.Message.Tool.Call>) -> Unit
) {
    val toolCalls = responses.filterIsInstance<com.lhzkml.jasmine.core.prompt.model.Message.Tool.Call>()
    if (toolCalls.isNotEmpty()) {
        action(toolCalls)
    }
}

/**
 * 如果 Message.Response 列表包含 Assistant 消息，执�?action
 * 移植�?koog �?onMultipleAssistantMessages(List<Message.Response>, (List<Message.Assistant>) -> Unit)
 */
@JvmName("onMultipleAssistantMessagesTyped")
inline fun AgentGraphContext.onMultipleAssistantMessages(
    responses: List<com.lhzkml.jasmine.core.prompt.model.Message.Response>,
    action: (List<com.lhzkml.jasmine.core.prompt.model.Message.Assistant>) -> Unit
) {
    val assistants = responses.filterIsInstance<com.lhzkml.jasmine.core.prompt.model.Message.Assistant>()
    if (assistants.isNotEmpty()) {
        action(assistants)
    }
}

/**
 * 检�?Message.Response 列表是否包含 Tool.Call
 * 移植�?koog �?List<Message.Response>.containsToolCalls()
 */
@JvmName("containsToolCallsTyped")
fun List<com.lhzkml.jasmine.core.prompt.model.Message.Response>.containsToolCalls(): Boolean =
    any { it is com.lhzkml.jasmine.core.prompt.model.Message.Tool.Call }

/**
 * �?Message.Response 列表中提取所�?Tool.Call
 * 移植�?koog �?extractToolCalls(List<Message.Response>)
 */
fun AgentGraphContext.extractToolCallMessages(
    responses: List<com.lhzkml.jasmine.core.prompt.model.Message.Response>
): List<com.lhzkml.jasmine.core.prompt.model.Message.Tool.Call> =
    responses.filterIsInstance<com.lhzkml.jasmine.core.prompt.model.Message.Tool.Call>()

/**
 * �?Message.Response 转为 Message.Assistant，如果不是则返回 null
 * 移植�?koog �?Message.Response.asAssistantMessageOrNull()
 */
fun com.lhzkml.jasmine.core.prompt.model.Message.Response.asAssistantOrNull(): com.lhzkml.jasmine.core.prompt.model.Message.Assistant? =
    this as? com.lhzkml.jasmine.core.prompt.model.Message.Assistant

/**
 * �?Message.Response 强制转为 Message.Assistant
 * 移植�?koog �?Message.Response.asAssistantMessage()
 */
fun com.lhzkml.jasmine.core.prompt.model.Message.Response.asAssistant(): com.lhzkml.jasmine.core.prompt.model.Message.Assistant =
    this as com.lhzkml.jasmine.core.prompt.model.Message.Assistant

/**
 * 获取最新的 token 用量（从 prompt 中读取）
 * 移植�?koog �?latestTokenUsage() (�?prompt.latestTokenUsage 读取)
 */
suspend fun AgentGraphContext.latestTokenUsageFromPrompt(): Int {
    return session.prompt.latestTokenUsage
}
