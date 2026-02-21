package com.lhzkml.jasmine.core.agent.tools.feature.handler

import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

// ========== LLM Call 事件上下文 ==========

interface LLMCallEventContext : AgentLifecycleEventContext

/** LLM 调用开始上下文 */
data class LLMCallStartingContext(
    override val eventId: String,
    val runId: String,
    val model: String,
    val messageCount: Int,
    val tools: List<String>,
    val context: AgentGraphContext
) : LLMCallEventContext {
    override val eventType = AgentLifecycleEventType.LLMCallStarting
}

/** LLM 调用完成上下文 */
data class LLMCallCompletedContext(
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
) : LLMCallEventContext {
    override val eventType = AgentLifecycleEventType.LLMCallCompleted
}

// ========== LLM Call 事件处理器 ==========

fun interface LLMCallStartingHandler {
    suspend fun handle(context: LLMCallStartingContext)
}

fun interface LLMCallCompletedHandler {
    suspend fun handle(context: LLMCallCompletedContext)
}

/**
 * LLM 调用事件处理器容器
 * 移植自 koog 的 LLMCallEventHandler。
 */
class LLMCallEventHandler {
    var llmCallStartingHandler: LLMCallStartingHandler = LLMCallStartingHandler { }
    var llmCallCompletedHandler: LLMCallCompletedHandler = LLMCallCompletedHandler { }
}
