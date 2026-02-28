package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.observe.trace.TraceError
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEvent
import com.lhzkml.jasmine.core.agent.observe.trace.StrategyGraph
import com.lhzkml.jasmine.core.agent.observe.trace.StrategyGraphNode
import com.lhzkml.jasmine.core.agent.observe.trace.StrategyGraphEdge

/**
 * Agent 策略
 * 移植�?koog �?AIAgentGraphStrategy，是子图的特殊形式，
 * 代表 Agent 的顶层执行策略�?
 *
 * 策略 = 子图 + 策略级别的追踪事�?
 *
 * @param name 策略名称
 * @param subgraph 策略对应的子�?
 */
class AgentStrategy<TInput, TOutput>(
    val name: String,
    val subgraph: AgentSubgraph<TInput, TOutput>
) {
    /** 获取策略的图结构元数据（用于追踪�?*/
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
