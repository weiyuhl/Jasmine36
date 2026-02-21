package com.lhzkml.jasmine.core.agent.tools.feature.handler

import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

// ========== Node Execution 事件上下文 ==========

interface NodeExecutionEventContext : AgentLifecycleEventContext

/** 节点开始执行上下文 */
data class NodeExecutionStartingContext(
    override val eventId: String,
    val nodeName: String,
    val input: String?,
    val context: AgentGraphContext
) : NodeExecutionEventContext {
    override val eventType = AgentLifecycleEventType.NodeExecutionStarting
}

/** 节点执行完成上下文 */
data class NodeExecutionCompletedContext(
    override val eventId: String,
    val nodeName: String,
    val input: String?,
    val output: String?,
    val context: AgentGraphContext
) : NodeExecutionEventContext {
    override val eventType = AgentLifecycleEventType.NodeExecutionCompleted
}

/** 节点执行失败上下文 */
data class NodeExecutionFailedContext(
    override val eventId: String,
    val nodeName: String,
    val input: String?,
    val throwable: Throwable,
    val context: AgentGraphContext
) : NodeExecutionEventContext {
    override val eventType = AgentLifecycleEventType.NodeExecutionFailed
}

// ========== Node Execution 事件处理器 ==========

fun interface NodeExecutionStartingHandler {
    suspend fun handle(context: NodeExecutionStartingContext)
}

fun interface NodeExecutionCompletedHandler {
    suspend fun handle(context: NodeExecutionCompletedContext)
}

fun interface NodeExecutionFailedHandler {
    suspend fun handle(context: NodeExecutionFailedContext)
}

/**
 * 节点执行事件处理器容器
 * 移植自 koog 的 NodeExecutionEventHandler。
 */
class NodeExecutionEventHandler {
    var nodeExecutionStartingHandler: NodeExecutionStartingHandler = NodeExecutionStartingHandler { }
    var nodeExecutionCompletedHandler: NodeExecutionCompletedHandler = NodeExecutionCompletedHandler { }
    var nodeExecutionFailedHandler: NodeExecutionFailedHandler = NodeExecutionFailedHandler { }
}
