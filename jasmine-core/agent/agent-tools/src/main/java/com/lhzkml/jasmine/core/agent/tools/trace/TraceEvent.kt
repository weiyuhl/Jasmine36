package com.lhzkml.jasmine.core.agent.tools.trace

import com.lhzkml.jasmine.core.agent.tools.graph.StrategyGraph

/**
 * 追踪事件基类
 * 参考 koog 的 DefinedFeatureEvent / FeatureMessage 体系，
 * 完整移植 koog 的 6 大类事件：Agent/Strategy/Node/Subgraph/LLM/Tool，
 * 并新增压缩事件。
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

    // ========== 策略执行（参考 koog StrategyStartingEvent / StrategyCompletedEvent） ==========

    /** 策略开始执行 */
    data class StrategyStarting(
        override val eventId: String,
        override val runId: String,
        val strategyName: String,
        val graph: StrategyGraph? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 策略执行完成 */
    data class StrategyCompleted(
        override val eventId: String,
        override val runId: String,
        val strategyName: String,
        val result: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    // ========== 节点执行（参考 koog NodeExecutionStartingEvent 等） ==========

    /** 节点开始执行 */
    data class NodeExecutionStarting(
        override val eventId: String,
        override val runId: String,
        val nodeName: String,
        val input: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 节点执行完成 */
    data class NodeExecutionCompleted(
        override val eventId: String,
        override val runId: String,
        val nodeName: String,
        val input: String?,
        val output: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 节点执行失败 */
    data class NodeExecutionFailed(
        override val eventId: String,
        override val runId: String,
        val nodeName: String,
        val input: String?,
        val error: TraceError,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    // ========== 子图执行（参考 koog SubgraphExecutionStartingEvent 等） ==========

    /** 子图开始执行 */
    data class SubgraphStarting(
        override val eventId: String,
        override val runId: String,
        val subgraphName: String,
        val input: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 子图执行完成 */
    data class SubgraphCompleted(
        override val eventId: String,
        override val runId: String,
        val subgraphName: String,
        val result: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TraceEvent()

    /** 子图执行失败 */
    data class SubgraphFailed(
        override val eventId: String,
        override val runId: String,
        val subgraphName: String,
        val error: TraceError,
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
