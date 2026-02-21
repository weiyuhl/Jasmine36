package com.lhzkml.jasmine.core.agent.tools.feature.handler

import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext
import com.lhzkml.jasmine.core.agent.tools.trace.TraceError

// ========== Tool Call 事件上下文 ==========

interface ToolCallEventContext : AgentLifecycleEventContext

/** 工具调用开始上下文 */
data class ToolCallStartingContext(
    override val eventId: String,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: String,
    val context: AgentGraphContext
) : ToolCallEventContext {
    override val eventType = AgentLifecycleEventType.ToolCallStarting
}

/** 工具参数验证失败上下文 */
data class ToolValidationFailedContext(
    override val eventId: String,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: String,
    val message: String,
    val error: TraceError,
    val context: AgentGraphContext
) : ToolCallEventContext {
    override val eventType = AgentLifecycleEventType.ToolValidationFailed
}

/** 工具调用失败上下文 */
data class ToolCallFailedContext(
    override val eventId: String,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: String,
    val message: String,
    val error: TraceError?,
    val context: AgentGraphContext
) : ToolCallEventContext {
    override val eventType = AgentLifecycleEventType.ToolCallFailed
}

/** 工具调用完成上下文 */
data class ToolCallCompletedContext(
    override val eventId: String,
    val runId: String,
    val toolCallId: String?,
    val toolName: String,
    val toolArgs: String,
    val toolResult: String?,
    val context: AgentGraphContext
) : ToolCallEventContext {
    override val eventType = AgentLifecycleEventType.ToolCallCompleted
}

// ========== Tool Call 事件处理器 ==========

fun interface ToolCallHandler {
    suspend fun handle(context: ToolCallStartingContext)
}

fun interface ToolValidationErrorHandler {
    suspend fun handle(context: ToolValidationFailedContext)
}

fun interface ToolCallFailureHandler {
    suspend fun handle(context: ToolCallFailedContext)
}

fun interface ToolCallResultHandler {
    suspend fun handle(context: ToolCallCompletedContext)
}

/**
 * 工具调用事件处理器容器
 * 移植自 koog 的 ToolCallEventHandler。
 */
class ToolCallEventHandler {
    var toolCallHandler: ToolCallHandler = ToolCallHandler { }
    var toolValidationErrorHandler: ToolValidationErrorHandler = ToolValidationErrorHandler { }
    var toolCallFailureHandler: ToolCallFailureHandler = ToolCallFailureHandler { }
    var toolCallResultHandler: ToolCallResultHandler = ToolCallResultHandler { }
}
