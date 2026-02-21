package com.lhzkml.jasmine.core.agent.tools.feature.handler

import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

// ========== LLM Streaming 事件上下文 ==========

interface LLMStreamingEventContext : AgentLifecycleEventContext

/** LLM 流式开始上下文 */
data class LLMStreamingStartingContext(
    override val eventId: String,
    val runId: String,
    val model: String,
    val messageCount: Int,
    val tools: List<String>,
    val context: AgentGraphContext
) : LLMStreamingEventContext {
    override val eventType = AgentLifecycleEventType.LLMStreamingStarting
}

/** LLM 流式帧接收上下文 */
data class LLMStreamingFrameReceivedContext(
    override val eventId: String,
    val runId: String,
    val chunk: String,
    val context: AgentGraphContext
) : LLMStreamingEventContext {
    override val eventType = AgentLifecycleEventType.LLMStreamingFrameReceived
}

/** LLM 流式失败上下文 */
data class LLMStreamingFailedContext(
    override val eventId: String,
    val runId: String,
    val model: String,
    val error: Throwable,
    val context: AgentGraphContext
) : LLMStreamingEventContext {
    override val eventType = AgentLifecycleEventType.LLMStreamingFailed
}

/** LLM 流式完成上下文 */
data class LLMStreamingCompletedContext(
    override val eventId: String,
    val runId: String,
    val model: String,
    val responsePreview: String?,
    val hasToolCalls: Boolean,
    val toolCallCount: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val context: AgentGraphContext
) : LLMStreamingEventContext {
    override val eventType = AgentLifecycleEventType.LLMStreamingCompleted
}

// ========== LLM Streaming 事件处理器 ==========

fun interface LLMStreamingStartingHandler {
    suspend fun handle(context: LLMStreamingStartingContext)
}

fun interface LLMStreamingFrameReceivedHandler {
    suspend fun handle(context: LLMStreamingFrameReceivedContext)
}

fun interface LLMStreamingFailedHandler {
    suspend fun handle(context: LLMStreamingFailedContext)
}

fun interface LLMStreamingCompletedHandler {
    suspend fun handle(context: LLMStreamingCompletedContext)
}

/**
 * LLM 流式事件处理器容器
 * 移植自 koog 的 LLMStreamingEventHandler。
 */
class LLMStreamingEventHandler {
    var llmStreamingStartingHandler: LLMStreamingStartingHandler = LLMStreamingStartingHandler { }
    var llmStreamingFrameReceivedHandler: LLMStreamingFrameReceivedHandler = LLMStreamingFrameReceivedHandler { }
    var llmStreamingFailedHandler: LLMStreamingFailedHandler = LLMStreamingFailedHandler { }
    var llmStreamingCompletedHandler: LLMStreamingCompletedHandler = LLMStreamingCompletedHandler { }
}
