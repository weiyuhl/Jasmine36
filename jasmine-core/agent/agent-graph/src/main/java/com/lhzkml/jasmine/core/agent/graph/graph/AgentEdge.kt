package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * Directed edge between nodes.
 *
 * @param TFrom source node output type
 * @param TTo target node input type
 * @param toNode target node
 * @param condition when non-null, returns null to skip this edge; when null, unconditional edge
 */
class AgentEdge<TFrom, TTo>(
    val toNode: BaseAgentNode,
    val condition: (suspend (TFrom) -> TTo?)? = null
)
