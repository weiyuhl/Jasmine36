package com.lhzkml.jasmine.core.agent.observe.trace

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
