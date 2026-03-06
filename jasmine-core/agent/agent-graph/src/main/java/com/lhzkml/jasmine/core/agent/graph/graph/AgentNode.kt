package com.lhzkml.jasmine.core.agent.graph.graph

import java.util.UUID

/**
 * Node execution context available inside node execute blocks.
 */
class AgentNodeContext(
    val storage: AgentStorage,
    val context: AgentGraphContext
)

/**
 * Base class for all graph nodes. Provides id/name/edges for metadata traversal.
 */
abstract class BaseAgentNode(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String
) {
    internal val _edges = mutableListOf<AgentEdge<*, *>>()
    val edges: List<AgentEdge<*, *>> get() = _edges
}

/**
 * Computation node that takes TInput and produces TOutput.
 */
class AgentNode<TInput, TOutput>(
    name: String,
    val execute: suspend AgentNodeContext.(TInput) -> TOutput
) : BaseAgentNode(name = name)

/**
 * Graph entry node. Passes input through to downstream nodes without computation.
 */
class StartNode<T>(name: String = "Start") : BaseAgentNode(name = name)

/**
 * Graph exit node. When reached, the current value is returned as the graph result.
 */
class FinishNode<T>(name: String = "Finish") : BaseAgentNode(name = name)
