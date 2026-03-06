package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * ??? DSL ????
 *
 * ??:
 * ```kotlin
 * val strategy = graphStrategy<String, String>("myStrategy") {
 *     val upper = node<String, String>("upper") { it.uppercase() }
 *     edge(nodeStart, upper)
 *     edge(upper, nodeFinish)
 * }
 * ```
 */
class GraphStrategyBuilder<TInput, TOutput>(private val name: String) {
    val nodeStart = StartNode<TInput>("Start")
    val nodeFinish = FinishNode<TOutput>("Finish")

    private val allNodes = mutableListOf<BaseAgentNode>(nodeStart, nodeFinish)

    fun <I, O> node(name: String, execute: suspend AgentNodeContext.(I) -> O): AgentNode<I, O> {
        val n = AgentNode(name, execute)
        allNodes.add(n)
        return n
    }

    fun edge(from: BaseAgentNode, to: BaseAgentNode) {
        from._edges.add(AgentEdge<Any?, Any?>(to, null))
    }

    fun <TFrom, TTo> conditionalEdge(
        from: AgentNode<*, TFrom>,
        to: BaseAgentNode,
        condition: suspend (TFrom) -> TTo?
    ) {
        from._edges.add(AgentEdge(to, condition))
    }

    internal fun build(): AgentStrategy<TInput, TOutput> {
        val subgraph = AgentSubgraph(
            name = name,
            startNode = nodeStart,
            finishNode = nodeFinish,
            nodes = allNodes.toList()
        )
        return AgentStrategy(name, subgraph)
    }
}

fun <TInput, TOutput> graphStrategy(
    name: String,
    block: GraphStrategyBuilder<TInput, TOutput>.() -> Unit
): AgentStrategy<TInput, TOutput> {
    return GraphStrategyBuilder<TInput, TOutput>(name).apply(block).build()
}
