package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * 图策�?DSL 构建�?
 * 移植�?koog �?AIAgentSubgraphBuilder / AIAgentGraphStrategyBuilder�?
 * 提供声明式的方式定义节点和边�?
 *
 * 支持两种风格�?
 *
 * 风格1 -- koog 风格（by 委托 + forwardTo）：
 * ```kotlin
 * val strategy = graphStrategy<String, String>("my-strategy") {
 *     val nodeCallLLM by nodeLLMRequest()
 *     val nodeExecTool by nodeExecuteTool()
 *     val nodeSendResult by nodeLLMSendToolResult()
 *
 *     edge(nodeStart forwardTo nodeCallLLM)
 *     edge(nodeCallLLM forwardTo nodeExecTool onToolCall { true })
 *     edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true } transformed { it.content })
 * }
 * ```
 *
 * 风格2 -- 简单风格（直接创建节点）：
 * ```kotlin
 * val strategy = graphStrategy<String, String>("my-strategy") {
 *     val process = node<String, String>("process") { input -> ... }
 *     edge(nodeStart, process)
 *     edge(process, nodeFinish)
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
     * 工具选择策略
     * 移植�?koog �?AIAgentSubgraph.toolSelectionStrategy�?
     * 决定子图执行时可用的工具集合�?
     *
     * 使用方式�?
     * ```kotlin
     * val strategy = graphStrategy<String, String>("my-strategy") {
     *     toolSelection = ToolSelectionStrategy.ByName(setOf("read_file", "write_file"))
     *     // ...
     * }
     * ```
     */
    var toolSelection: ToolSelectionStrategy = ToolSelectionStrategy.ALL

    /**
     * 定义一个节点（直接返回�?
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
     * 定义一个节点委托（支持 by 语法�?
     * 移植�?koog �?node() 返回 AIAgentNodeDelegate
     *
     * 使用方式�?
     * ```kotlin
     * val myNode by node<String, String> { input -> ... }
     * ```
     */
    fun <I, O> node(
        name: String? = null,
        execute: suspend AgentGraphContext.(input: I) -> O
    ): AgentNodeDelegate<I, O> {
        return AgentNodeDelegate(name, execute)
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
     * 添加条件边（带转�?过滤�?
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

    /**
     * 通过 EdgeBuilder 添加边（支持 forwardTo DSL�?
     * 移植�?koog �?edge(edgeIntermediate) 方法
     *
     * 使用方式�?
     * ```kotlin
     * edge(nodeA forwardTo nodeB)
     * edge(nodeA forwardTo nodeB onToolCall { true })
     * edge(nodeA forwardTo nodeFinish onAssistantMessage { true } transformed { it.content })
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    fun <TFrom, TIntermediate, TTo> edge(
        edgeBuilder: EdgeBuilder<TFrom, TIntermediate, TTo>
    ) {
        val edge = edgeBuilder.build()
        (edgeBuilder.fromNode as AgentNode<Any?, TFrom>).addEdge(edge)
    }

    internal fun build(): AgentStrategy<TInput, TOutput> {
        val subgraph = AgentSubgraph(name, nodeStart, nodeFinish, toolSelectionStrategy = toolSelection)
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
