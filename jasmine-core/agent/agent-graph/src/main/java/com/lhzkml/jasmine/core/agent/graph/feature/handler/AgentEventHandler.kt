package com.lhzkml.jasmine.core.agent.graph.feature.handler

import com.lhzkml.jasmine.core.agent.graph.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.graph.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.graph.graph.AgentGraphContext

// ========== Agent 事件上下�?==========

/** Agent 事件上下文基础接口 */
interface AgentEventContext : AgentLifecycleEventContext

/** Agent 开始执行上下文 */
data class AgentStartingContext(
    override val eventId: String,
    val agentId: String,
    val runId: String,
    val context: AgentGraphContext
) : AgentEventContext {
    override val eventType = AgentLifecycleEventType.AgentStarting
}

/** Agent 执行完成上下�?*/
data class AgentCompletedContext(
    override val eventId: String,
    val agentId: String,
    val runId: String,
    val result: Any?,
    val context: AgentGraphContext
) : AgentEventContext {
    override val eventType = AgentLifecycleEventType.AgentCompleted
}

/** Agent 执行失败上下�?*/
data class AgentExecutionFailedContext(
    override val eventId: String,
    val agentId: String,
    val runId: String,
    val throwable: Throwable,
    val context: AgentGraphContext
) : AgentEventContext {
    override val eventType = AgentLifecycleEventType.AgentExecutionFailed
}

/** Agent 关闭前上下文 */
data class AgentClosingContext(
    override val eventId: String,
    val agentId: String
) : AgentEventContext {
    override val eventType = AgentLifecycleEventType.AgentClosing
}

// ========== Agent 事件处理�?==========

/** Agent 开始处理器 */
fun interface AgentStartingHandler {
    suspend fun handle(context: AgentStartingContext)
}

/** Agent 完成处理�?*/
fun interface AgentCompletedHandler {
    suspend fun handle(context: AgentCompletedContext)
}

/** Agent 失败处理�?*/
fun interface AgentExecutionFailedHandler {
    suspend fun handle(context: AgentExecutionFailedContext)
}

/** Agent 关闭处理�?*/
fun interface AgentClosingHandler {
    suspend fun handle(context: AgentClosingContext)
}

/**
 * Agent 事件处理器容�?
 * 移植�?koog �?AgentEventHandler�?
 */
class AgentEventHandler {
    var agentStartingHandler: AgentStartingHandler = AgentStartingHandler { }
    var agentCompletedHandler: AgentCompletedHandler = AgentCompletedHandler { }
    var agentExecutionFailedHandler: AgentExecutionFailedHandler = AgentExecutionFailedHandler { }
    var agentClosingHandler: AgentClosingHandler = AgentClosingHandler { }

    suspend fun handleAgentStarting(context: AgentStartingContext) {
        agentStartingHandler.handle(context)
    }
}
