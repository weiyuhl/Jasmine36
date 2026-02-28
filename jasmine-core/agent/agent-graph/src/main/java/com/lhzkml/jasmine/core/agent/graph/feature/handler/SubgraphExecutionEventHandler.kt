package com.lhzkml.jasmine.core.agent.graph.feature.handler

import com.lhzkml.jasmine.core.agent.graph.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.graph.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.graph.graph.AgentGraphContext

// ========== Subgraph Execution 事件上下�?==========

interface SubgraphExecutionEventContext : AgentLifecycleEventContext

/** 子图开始执行上下文 */
data class SubgraphExecutionStartingContext(
    override val eventId: String,
    val subgraphName: String,
    val input: String?,
    val context: AgentGraphContext
) : SubgraphExecutionEventContext {
    override val eventType = AgentLifecycleEventType.SubgraphExecutionStarting
}

/** 子图执行完成上下�?*/
data class SubgraphExecutionCompletedContext(
    override val eventId: String,
    val subgraphName: String,
    val input: String?,
    val output: String?,
    val context: AgentGraphContext
) : SubgraphExecutionEventContext {
    override val eventType = AgentLifecycleEventType.SubgraphExecutionCompleted
}

/** 子图执行失败上下�?*/
data class SubgraphExecutionFailedContext(
    override val eventId: String,
    val subgraphName: String,
    val input: String?,
    val throwable: Throwable,
    val context: AgentGraphContext
) : SubgraphExecutionEventContext {
    override val eventType = AgentLifecycleEventType.SubgraphExecutionFailed
}

// ========== Subgraph Execution 事件处理�?==========

fun interface SubgraphExecutionStartingHandler {
    suspend fun handle(context: SubgraphExecutionStartingContext)
}

fun interface SubgraphExecutionCompletedHandler {
    suspend fun handle(context: SubgraphExecutionCompletedContext)
}

fun interface SubgraphExecutionFailedHandler {
    suspend fun handle(context: SubgraphExecutionFailedContext)
}

/**
 * 子图执行事件处理器容�?
 * 移植�?koog �?SubgraphExecutionEventHandler�?
 */
class SubgraphExecutionEventHandler {
    var subgraphExecutionStartingHandler: SubgraphExecutionStartingHandler = SubgraphExecutionStartingHandler { }
    var subgraphExecutionCompletedHandler: SubgraphExecutionCompletedHandler = SubgraphExecutionCompletedHandler { }
    var subgraphExecutionFailedHandler: SubgraphExecutionFailedHandler = SubgraphExecutionFailedHandler { }
}
