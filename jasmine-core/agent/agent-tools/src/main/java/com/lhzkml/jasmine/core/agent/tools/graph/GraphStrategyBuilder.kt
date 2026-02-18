package com.lhzkml.jasmine.core.agent.tools.graph

/**
 * 图策略 DSL 构建器
 * 参考 koog 的 AIAgentSubgraphBuilder / AIAgentGraphStrategyBuilder，
 * 提供声明式的方式定义节点和边。
 *
 * 使用方式：
 * ```kotlin
 * val strategy = graphStrategy<String, String>("my-strategy") {
 *     val processInput by node<String, String>("processInput") { input ->
 *         "processed: $input"
 *     }
 *
 *     val callLLM by node<String, String>("callLLM") { input ->
 *         session.requestLLM().content
 *     }
 *
 *     edge(nodeStart, processInput)
 *     edge(processInput, callLLM)
 *     edge(callLLM, nodeFinish)
 * }
 * ```
 */
@DslMarker
annotation class GraphBuilderDsl

@GraphBuilderDsl
class GraphStrategyBuilder<TInput, TOutput>(
    private val name: String
) {
    val nodeStart = StartNode<TInput>(name)
    val nodeFinish = FinishNode<TOutput>(name)
    private val nodes = mutableListOf<AgentNode<*, *>>()

    /**
     * 定义一个节点
     */
    fun <I, O> node(
        name: String,
        execute: suspend AgentGraphContext.(input: I) -> O
    ): AgentNode<I, O> {
        val node = AgentNode(name, execute)
        nodes.add(node)
        return node
    }

    /**
     * 添加无条件边（直接传递输出）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> edge(from: AgentNode<*, T>, to: AgentNode<T, *>) {
        val edge = AgentEdge<T, T>(to as AgentNode<T, *>) { output -> output }
        (from as AgentNode<Any?, T>).addEdge(edge)
    }

    /**
     * 添加条件边（带转换/过滤）
     */
    @Suppress("UNCHECKED_CAST")
    fun <TFrom, TTo> conditionalEdge(
        from: AgentNode<*, TFrom>,
        to: AgentNode<TTo, *>,
        condition: suspend AgentGraphContext.(output: TFrom) -> TTo?
    ) {
        val edge = AgentEdge(to, condition)
        (from as AgentNode<Any?, TFrom>).addEdge(edge)
    }

    internal fun build(): AgentStrategy<TInput, TOutput> {
        val subgraph = AgentSubgraph(name, nodeStart, nodeFinish)
        for (node in nodes) {
            subgraph.registerNode(node)
        }
        return AgentStrategy(name, subgraph)
    }
}

/**
 * DSL 入口：构建图策略
 */
fun <TInput, TOutput> graphStrategy(
    name: String,
    block: GraphStrategyBuilder<TInput, TOutput>.() -> Unit
): AgentStrategy<TInput, TOutput> {
    return GraphStrategyBuilder<TInput, TOutput>(name).apply(block).build()
}
