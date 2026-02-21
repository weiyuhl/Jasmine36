package com.lhzkml.jasmine.core.agent.tools.feature.handler

import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

// ========== Subgraph Execution 事件上下文 ==========

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

/** 子图执行完成上下文 */
data class SubgraphExecutionCompletedContext(
    override val eventId: String,
    val subgraphName: String,
    val input: String?,
    val output: String?,
    val context: AgentGraphContext
) : SubgraphExecutionEventContext {
    override val eventType = AgentLifecycleEventType.SubgraphExecutionCompleted
}

/** 子图执行失败上下文 */
data class SubgraphExecutionFailedContext(
    override val eventId: String,
    val subgraphName: String,
    val input: String?,
    val throwable: Throwable,
    val context: AgentGraphContext
) : SubgraphExecutionEventContext {
    override val eventType = AgentLifecycleEventType.SubgraphExecutionFailed
}

// ========== Subgraph Execution 事件处理器 ==========

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
 * 子图执行事件处理器容器
 * 移植自 koog 的 SubgraphExecutionEventHandler。
 */
class SubgraphExecutionEventHandler {
    var subgraphExecutionStartingHandler: SubgraphExecutionStartingHandler = SubgraphExecutionStartingHandler { }
    var subgraphExecutionCompletedHandler: SubgraphExecutionCompletedHandler = SubgraphExecutionCompletedHandler { }
    var subgraphExecutionFailedHandler: SubgraphExecutionFailedHandler = SubgraphExecutionFailedHandler { }
}
