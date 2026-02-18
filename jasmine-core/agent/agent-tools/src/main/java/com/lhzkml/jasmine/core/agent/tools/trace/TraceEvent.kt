package com.lhzkml.jasmine.core.agent.tools.trace

/**
 * 追踪事件基类
 * 参考 koog 的 DefinedFeatureEvent / FeatureMessage 体系，
 * 简化为适合 jasmine 的扁平结构。
 *
 * koog 有 6 大类 18 种事件（Agent/Strategy/Node/Subgraph/LLM/Tool），
 * jasmine 没有 graph-based agent，所以去掉 Strategy/Node/Subgraph，
 * 保留 Agent 生命周期、LLM 调用、LLM 流式、工具调用，并新增压缩事件。
 */
sealed class TraceEvent {
    /** 事件唯一 ID */
    abstract val eventId: String
    /** 运行 ID（一次 agent 执行的唯一标识） */
    abstract val runId: String
    /** 时间戳（毫秒） */
    abstract val timestamp: Long

    // ========== Agent 生命周期 ==========

    /** Agent 开始执行 */
    data class AgentStarting(
        override val eventId: String,
        override val runId: String,
        val agentId: String,
        val model: String,
        val toolCount: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** Agent 执行完成 */
    data class AgentCompleted(
        override val eventId: String,
        override val runId: String,
        val agentId: String,
        val result: String?,
        val totalIterations: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** Agent 执行失败 */
    data class AgentFailed(
        override val eventId: String,
        override val runId: String,
        val agentId: String,
        val error: TraceError,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    // ========== LLM 调用 ==========

    /** LLM 调用开始 */
    data class LLMCallStarting(
        override val eventId: String,
        override val runId: String,
        val model: String,
        val messageCount: Int,
        val tools: List<String>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** LLM 调用完成 */
    data class LLMCallCompleted(
        override val eventId: String,
        override val runId: String,
        val model: String,
        val responsePreview: String?,
        val hasToolCalls: Boolean,
        val toolCallCount: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    // ========== LLM 流式 ==========

    /** LLM 流式开始 */
    data class LLMStreamStarting(
        override val eventId: String,
        override val runId: String,
        val model: String,
        val messageCount: Int,
        val tools: List<String>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** LLM 流式帧 */
    data class LLMStreamFrame(
        override val eventId: String,
        override val runId: String,
        val chunk: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** LLM 流式完成 */
    data class LLMStreamCompleted(
        override val eventId: String,
        override val runId: String,
        val model: String,
        val responsePreview: String?,
        val hasToolCalls: Boolean,
        val toolCallCount: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** LLM 流式失败 */
    data class LLMStreamFailed(
        override val eventId: String,
        override val runId: String,
        val model: String,
        val error: TraceError,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    // ========== 工具调用 ==========

    /** 工具调用开始 */
    data class ToolCallStarting(
        override val eventId: String,
        override val runId: String,
        val toolCallId: String?,
        val toolName: String,
        val toolArgs: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 工具调用完成 */
    data class ToolCallCompleted(
        override val eventId: String,
        override val runId: String,
        val toolCallId: String?,
        val toolName: String,
        val toolArgs: String,
        val result: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 工具调用失败 */
    data class ToolCallFailed(
        override val eventId: String,
        override val runId: String,
        val toolCallId: String?,
        val toolName: String,
        val toolArgs: String,
        val error: TraceError,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    // ========== 上下文压缩 ==========

    /** 压缩开始 */
    data class CompressionStarting(
        override val eventId: String,
        override val runId: String,
        val strategyName: String,
        val originalMessageCount: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 压缩完成 */
    data class CompressionCompleted(
        override val eventId: String,
        override val runId: String,
        val strategyName: String,
        val compressedMessageCount: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()
}

/**
 * 追踪错误信息
 * 参考 koog 的 AIAgentError
 */
data class TraceError(
    val message: String?,
    val stackTrace: String? = null,
    val cause: String? = null
) {
    companion object {
        fun from(throwable: Throwable): TraceError = TraceError(
            message = throwable.message,
            stackTrace = throwable.stackTraceToString().take(500),
            cause = throwable.cause?.message
        )
    }
}
