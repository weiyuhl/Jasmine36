package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.agent.observe.trace.TraceError
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEvent
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.llm.compressIfNeeded
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.Usage
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.coroutineContext

/**
 * Agent 执行过程事件监听器
 * 用于在 UI 层显示工具调用、结果、压缩等中间过程
 */
interface AgentEventListener {
    /** 工具调用开始 */
    suspend fun onToolCallStart(toolName: String, arguments: String) {}
    /** 工具调用完成 */
    suspend fun onToolCallResult(toolName: String, result: String) {}
    /** 思考/推理内容 */
    suspend fun onThinking(content: String) {}
    /** 上下文压缩 */
    suspend fun onCompression(message: String) {}
    /** Agent 显式完成任务（attempt_completion） */
    suspend fun onCompletion(result: String, command: String?) {}
}

/**
 * Agent 显式完成信号
 * 当 LLM 调用 attempt_completion 时抛出，用于终止 agent loop
 */
class AgentCompletionSignal(
    val result: String,
    val command: String? = null
) : Exception("Agent completed")

/**
 * 工具执行器 — Agent Loop
 * 参考 koog 的 agent 执行策略，实现自动工具调用循环：
 *
 * 1. 发送消息给 LLM（附带工具描述）
 * 2. 如果 LLM 返回 tool_calls → 执行工具 → 将结果追加到 prompt → 回到步骤 1
 * 3. 如果 LLM 返回普通文本 → 结束循环，返回最终结果
 *
 */
class ToolExecutor(
    private val client: ChatClient,
    private val registry: ToolRegistry,
    private val maxIterations: Int = 10,
    private val compressionStrategy: HistoryCompressionStrategy.TokenBudget? = null,
    private val eventListener: AgentEventListener? = null,
    private val tracing: Tracing? = null,
    private val maxToolResultLength: Int = MAX_TOOL_RESULT_LENGTH
) {
    // ========== Prompt + LLMSession 方式 ==========

    // 迭代次数 holder，用于在内部循环和外部之间传递
    private class IterationHolder(var count: Int = 0)

    /**
     * 使用 Prompt 执行 agent loop（非流式）
     * LLMSession 自动管理提示词累积
     */
    suspend fun execute(prompt: Prompt, model: String): ChatResult {
        val runId = tracing?.newRunId() ?: ""
        val session = LLMWriteSession(client, model, prompt, registry.descriptors())
        val iterHolder = IterationHolder()

        tracing?.emit(TraceEvent.AgentStarting(
            eventId = tracing.newEventId(), runId = runId,
            agentId = prompt.id, model = model, toolCount = registry.descriptors().size
        ))

        return try {
            val result = session.use { executeLoop(it, runId, iterHolder) }
            tracing?.emit(TraceEvent.AgentCompleted(
                eventId = tracing.newEventId(), runId = runId,
                agentId = prompt.id, result = result.content.take(100),
                totalIterations = iterHolder.count
            ))
            result
        } catch (e: Exception) {
            tracing?.emit(TraceEvent.AgentFailed(
                eventId = tracing.newEventId(), runId = runId,
                agentId = prompt.id, error = TraceError.from(e)
            ))
            throw e
        }
    }

    /**
     * 使用 Prompt 执行 agent loop（流式）
     */
    suspend fun executeStream(
        prompt: Prompt,
        model: String,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        val runId = tracing?.newRunId() ?: ""
        val session = LLMWriteSession(client, model, prompt, registry.descriptors())
        val iterHolder = IterationHolder()

        tracing?.emit(TraceEvent.AgentStarting(
            eventId = tracing.newEventId(), runId = runId,
            agentId = prompt.id, model = model, toolCount = registry.descriptors().size
        ))

        return try {
            val result = session.use { executeStreamLoop(it, onChunk, runId, iterHolder) }
            tracing?.emit(TraceEvent.AgentCompleted(
                eventId = tracing.newEventId(), runId = runId,
                agentId = prompt.id, result = result.content.take(100),
                totalIterations = iterHolder.count
            ))
            result
        } catch (e: Exception) {
            tracing?.emit(TraceEvent.AgentFailed(
                eventId = tracing.newEventId(), runId = runId,
                agentId = prompt.id, error = TraceError.from(e)
            ))
            throw e
        }
    }

    private suspend fun executeLoop(session: LLMWriteSession, runId: String = "", iterHolder: IterationHolder = IterationHolder()): ChatResult {
        var totalUsage = Usage(0, 0, 0)
        var iterations = 0

        while (iterations < maxIterations) {
            coroutineContext.ensureActive()
            iterations++
            iterHolder.count = iterations

            // 自动压缩检查
            compressionStrategy?.let {
                if (it.shouldCompress(session.prompt.messages)) {
                    tracing?.emit(TraceEvent.CompressionStarting(
                        eventId = tracing.newEventId(), runId = runId,
                        strategyName = "TokenBudget", originalMessageCount = session.prompt.messages.size
                    ))
                    session.compressIfNeeded(it)
                    tracing?.emit(TraceEvent.CompressionCompleted(
                        eventId = tracing.newEventId(), runId = runId,
                        strategyName = "TokenBudget", compressedMessageCount = session.prompt.messages.size
                    ))
                }
            }

            // LLM 调用追踪
            tracing?.emit(TraceEvent.LLMCallStarting(
                eventId = tracing.newEventId(), runId = runId,
                model = session.model, messageCount = session.prompt.messages.size,
                tools = session.tools.map { it.name }
            ))

            val result = session.requestLLM()
            totalUsage = totalUsage.add(result.usage)

            tracing?.emit(TraceEvent.LLMCallCompleted(
                eventId = tracing.newEventId(), runId = runId,
                model = session.model, responsePreview = result.content.take(100),
                hasToolCalls = result.hasToolCalls, toolCallCount = result.toolCalls.size,
                promptTokens = result.usage?.promptTokens ?: 0,
                completionTokens = result.usage?.completionTokens ?: 0,
                totalTokens = result.usage?.totalTokens ?: 0
            ))

            // 通知思考内容
            result.thinking?.let { eventListener?.onThinking(it) }

            if (!result.hasToolCalls) return result.copy(usage = totalUsage)

            // 通知工具调用并执行
            for (call in result.toolCalls) {
                coroutineContext.ensureActive()

                // 检测 attempt_completion 显式完成信号
                if (call.name == COMPLETION_TOOL_NAME) {
                    val signal = parseCompletionSignal(call.arguments)
                    eventListener?.onCompletion(signal.result, signal.command)

                    tracing?.emit(TraceEvent.AgentCompleted(
                        eventId = tracing.newEventId(), runId = runId,
                        agentId = "", result = signal.result.take(100),
                        totalIterations = iterations
                    ))

                    val content = buildString {
                        append(signal.result)
                        signal.command?.let { append("\n\n[建议操作] $it") }
                    }
                    return ChatResult(
                        content = content,
                        usage = totalUsage,
                        finishReason = "completion"
                    )
                }

                eventListener?.onToolCallStart(call.name, call.arguments)

                tracing?.emit(TraceEvent.ToolCallStarting(
                    eventId = tracing.newEventId(), runId = runId,
                    toolCallId = call.id, toolName = call.name, toolArgs = call.arguments
                ))

                try {
                    val toolResult = registry.execute(call)
                    eventListener?.onToolCallResult(call.name, toolResult.content)

                    tracing?.emit(TraceEvent.ToolCallCompleted(
                        eventId = tracing.newEventId(), runId = runId,
                        toolCallId = call.id, toolName = call.name,
                        toolArgs = call.arguments, result = toolResult.content.take(200)
                    ))

                    session.appendPrompt { message(ChatMessage.toolResult(truncateToolResult(toolResult))) }
                } catch (e: Exception) {
                    tracing?.emit(TraceEvent.ToolCallFailed(
                        eventId = tracing.newEventId(), runId = runId,
                        toolCallId = call.id, toolName = call.name,
                        toolArgs = call.arguments, error = TraceError.from(e)
                    ))
                    throw e
                }
            }
        }

        // 到达最大迭代次数，给模型一次无工具的总结机会
        session.appendPrompt {
            user("你已经进行了 $maxIterations 轮工具调用。请根据目前收集到的信息，直接给出总结回复，不要再调用工具。")
        }
        val finalResult = session.requestLLMWithoutTools()
        totalUsage = totalUsage.add(finalResult.usage)
        return ChatResult(
            content = finalResult.content,
            usage = totalUsage, finishReason = "max_iterations"
        )
    }

    private suspend fun executeStreamLoop(
        session: LLMWriteSession,
        onChunk: suspend (String) -> Unit,
        runId: String = "",
        iterHolder: IterationHolder = IterationHolder()
    ): StreamResult {
        var totalUsage = Usage(0, 0, 0)
        var iterations = 0

        // 构建 onThinking 回调，实时转发给 eventListener
        val onThinking: suspend (String) -> Unit = if (eventListener != null) {
            { text -> eventListener.onThinking(text) }
        } else {
            {}
        }

        while (iterations < maxIterations) {
            coroutineContext.ensureActive()
            iterations++
            iterHolder.count = iterations

            // 自动压缩检查
            compressionStrategy?.let {
                if (it.shouldCompress(session.prompt.messages)) {
                    tracing?.emit(TraceEvent.CompressionStarting(
                        eventId = tracing.newEventId(), runId = runId,
                        strategyName = "TokenBudget", originalMessageCount = session.prompt.messages.size
                    ))
                    session.compressIfNeeded(it)
                    tracing?.emit(TraceEvent.CompressionCompleted(
                        eventId = tracing.newEventId(), runId = runId,
                        strategyName = "TokenBudget", compressedMessageCount = session.prompt.messages.size
                    ))
                }
            }

            // LLM 流式调用追踪
            tracing?.emit(TraceEvent.LLMStreamStarting(
                eventId = tracing.newEventId(), runId = runId,
                model = session.model, messageCount = session.prompt.messages.size,
                tools = session.tools.map { it.name }
            ))

            val tracingOnChunk: suspend (String) -> Unit = { chunk ->
                onChunk(chunk)
                tracing?.emit(TraceEvent.LLMStreamFrame(
                    eventId = tracing.newEventId(), runId = runId, chunk = chunk
                ))
            }

            val result = try {
                session.requestLLMStream(tracingOnChunk, onThinking)
            } catch (e: Exception) {
                tracing?.emit(TraceEvent.LLMStreamFailed(
                    eventId = tracing.newEventId(), runId = runId,
                    model = session.model, error = TraceError.from(e)
                ))
                throw e
            }

            totalUsage = totalUsage.add(result.usage)

            tracing?.emit(TraceEvent.LLMStreamCompleted(
                eventId = tracing.newEventId(), runId = runId,
                model = session.model, responsePreview = result.content.take(100),
                hasToolCalls = result.hasToolCalls, toolCallCount = result.toolCalls.size,
                promptTokens = result.usage?.promptTokens ?: 0,
                completionTokens = result.usage?.completionTokens ?: 0,
                totalTokens = result.usage?.totalTokens ?: 0
            ))

            if (!result.hasToolCalls) return result.copy(usage = totalUsage)

            // 通知工具调用并执行
            for (call in result.toolCalls) {
                coroutineContext.ensureActive()

                // 检测 attempt_completion 显式完成信号
                if (call.name == COMPLETION_TOOL_NAME) {
                    val signal = parseCompletionSignal(call.arguments)
                    eventListener?.onCompletion(signal.result, signal.command)

                    tracing?.emit(TraceEvent.AgentCompleted(
                        eventId = tracing.newEventId(), runId = runId,
                        agentId = "", result = signal.result.take(100),
                        totalIterations = iterations
                    ))

                    val content = buildString {
                        append(signal.result)
                        signal.command?.let { append("\n\n[建议操作] $it") }
                    }
                    return StreamResult(
                        content = content,
                        usage = totalUsage,
                        finishReason = "completion"
                    )
                }

                eventListener?.onToolCallStart(call.name, call.arguments)

                tracing?.emit(TraceEvent.ToolCallStarting(
                    eventId = tracing.newEventId(), runId = runId,
                    toolCallId = call.id, toolName = call.name, toolArgs = call.arguments
                ))

                try {
                    val toolResult = registry.execute(call)
                    eventListener?.onToolCallResult(call.name, toolResult.content)

                    tracing?.emit(TraceEvent.ToolCallCompleted(
                        eventId = tracing.newEventId(), runId = runId,
                        toolCallId = call.id, toolName = call.name,
                        toolArgs = call.arguments, result = toolResult.content.take(200)
                    ))

                    session.appendPrompt { message(ChatMessage.toolResult(truncateToolResult(toolResult))) }
                } catch (e: Exception) {
                    tracing?.emit(TraceEvent.ToolCallFailed(
                        eventId = tracing.newEventId(), runId = runId,
                        toolCallId = call.id, toolName = call.name,
                        toolArgs = call.arguments, error = TraceError.from(e)
                    ))
                    throw e
                }
            }
        }

        // 到达最大迭代次数，给模型一次无工具的流式总结机会
        session.appendPrompt {
            user("你已经进行了 $maxIterations 轮工具调用。请根据目前收集到的信息，直接给出总结回复，不要再调用工具。")
        }
        val finalResult = session.requestLLMStreamWithoutTools(onChunk, onThinking)
        totalUsage = totalUsage.add(finalResult.usage)
        return StreamResult(
            content = finalResult.content,
            usage = totalUsage, finishReason = "max_iterations"
        )
    }

    private fun Usage.add(other: Usage?): Usage {
        if (other == null) return this
        return Usage(
            promptTokens + other.promptTokens,
            completionTokens + other.completionTokens,
            totalTokens + other.totalTokens
        )
    }

    private suspend fun <T> LLMWriteSession.use(block: suspend (LLMWriteSession) -> T): T {
        try {
            return block(this)
        } finally {
            close()
        }
    }

    companion object {
        /** attempt_completion 工具名 */
        const val COMPLETION_TOOL_NAME = "attempt_completion"
        /** 工具结果最大字符数，超过则截断 */
        const val MAX_TOOL_RESULT_LENGTH = 8000
    }

    /**
     * 截断过长的工具结果，避免 prompt 膨胀
     */
    private fun truncateToolResult(result: com.lhzkml.jasmine.core.prompt.model.ToolResult): com.lhzkml.jasmine.core.prompt.model.ToolResult {
        if (result.content.length <= maxToolResultLength) return result
        val truncated = result.content.take(maxToolResultLength) +
            "\n\n[结果已截断，原始长度: ${result.content.length} 字符，显示前 $maxToolResultLength 字符]"
        return result.copy(content = truncated)
    }

    /**
     * 解析 attempt_completion 的参数
     */
    private fun parseCompletionSignal(arguments: String): AgentCompletionSignal {
        return try {
            val obj = Json.parseToJsonElement(arguments).jsonObject
            val result = obj["result"]?.jsonPrimitive?.content ?: ""
            val command = obj["command"]?.jsonPrimitive?.content
            AgentCompletionSignal(result, command)
        } catch (_: Exception) {
            AgentCompletionSignal(arguments)
        }
    }
}
