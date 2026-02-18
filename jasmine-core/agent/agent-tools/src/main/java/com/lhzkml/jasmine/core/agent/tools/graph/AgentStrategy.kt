package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.trace.TraceError
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEvent

/**
 * Agent 策略
 * 参考 koog 的 AIAgentGraphStrategy，是子图的特殊形式，
 * 代表 Agent 的顶层执行策略。
 *
 * 策略 = 子图 + 策略级别的追踪事件
 *
 * @param name 策略名称
 * @param subgraph 策略对应的子图
 */
class AgentStrategy<TInput, TOutput>(
    val name: String,
    val subgraph: AgentSubgraph<TInput, TOutput>
) {
    /** 获取策略的图结构元数据（用于追踪） */
    fun graphMetadata(): StrategyGraph {
        val graphNodes = subgraph.nodes.map { StrategyGraphNode(it.id, it.name) }
        val graphEdges = mutableListOf<StrategyGraphEdge>()
        for (node in subgraph.nodes) {
            for (edge in node.edges) {
                graphEdges.add(StrategyGraphEdge(
                    sourceNode = StrategyGraphNode(node.id, node.name),
                    targetNode = StrategyGraphNode(edge.toNode.id, edge.toNode.name)
                ))
            }
        }
        return StrategyGraph(graphNodes, graphEdges)
    }

    /**
     * 执行策略
     */
    suspend fun execute(context: AgentGraphContext, input: TInput): TOutput? {
        val tracing = context.tracing
        context.strategyName = name

        tracing?.emit(TraceEvent.StrategyStarting(
            eventId = tracing.newEventId(), runId = context.runId,
            strategyName = name, graph = graphMetadata()
        ))

        return try {
            val result = subgraph.execute(context, input)
            tracing?.emit(TraceEvent.StrategyCompleted(
                eventId = tracing.newEventId(), runId = context.runId,
                strategyName = name, result = result.toString().take(100)
            ))
            result
        } catch (e: Exception) {
            tracing?.emit(TraceEvent.StrategyCompleted(
                eventId = tracing.newEventId(), runId = context.runId,
                strategyName = name, result = "ERROR: ${e.message}"
            ))
            throw e
        }
    }
}

/**
 * 策略图结构元数据
 * 参考 koog 的 StrategyEventGraph
 */
data class StrategyGraph(
    val nodes: List<StrategyGraphNode>,
    val edges: List<StrategyGraphEdge>
)

data class StrategyGraphNode(
    val id: String,
    val name: String
)

data class StrategyGraphEdge(
    val sourceNode: StrategyGraphNode,
    val targetNode: StrategyGraphNode
)
