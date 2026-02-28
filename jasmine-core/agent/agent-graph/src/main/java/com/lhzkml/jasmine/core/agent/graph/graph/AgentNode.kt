package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * Agent 图节�?
 * 移植�?koog �?AIAgentNode / AIAgentNodeBase�?
 *
 * @param name 节点名称（唯一标识�?
 * @param execute 节点执行函数，接收上下文和输入，返回输出
 */
open class AgentNode<TInput, TOutput>(
    val name: String,
    val execute: suspend AgentGraphContext.(input: TInput) -> TOutput
) {
    val id: String get() = name
    internal val edges = mutableListOf<AgentEdge<TOutput, *>>()

    fun addEdge(edge: AgentEdge<TOutput, *>) {
        edges.add(edge)
    }

    /**
     * 解析出边：找到第一个条件匹配的�?
     */
    suspend fun resolveEdge(context: AgentGraphContext, output: TOutput): ResolvedEdge? {
        for (edge in edges) {
            @Suppress("UNCHECKED_CAST")
            val result = (edge as AgentEdge<Any?, Any?>).tryForward(context, output)
            if (result != null) {
                return ResolvedEdge(edge.toNode, result)
            }
        }
        return null
    }

    data class ResolvedEdge(val node: AgentNode<*, *>, val input: Any?)
}

/**
 * 起始节点 -- 直接传递输�?
 * 移植�?koog �?StartNode
 */
class StartNode<T>(subgraphName: String? = null) : AgentNode<T, T>(
    name = subgraphName?.let { "__start__$it" } ?: "__start__",
    execute = { input -> input }
)

/**
 * 结束节点 -- 直接传递输入作为输�?
 * 移植�?koog �?FinishNode
 */
class FinishNode<T>(subgraphName: String? = null) : AgentNode<T, T>(
    name = subgraphName?.let { "__finish__$it" } ?: "__finish__",
    execute = { input -> input }
)

/**
 * 节点委托
 * 移植�?koog �?AIAgentNodeDelegate，支�?by 委托语法延迟创建节点�?
 *
 * 使用方式�?
 * ```kotlin
 * val myNode by node<String, String>("myNode") { input -> ... }
 * ```
 */
class AgentNodeDelegate<TInput, TOutput>(
    private val name: String?,
    private val execute: suspend AgentGraphContext.(input: TInput) -> TOutput
) {
    private var node: AgentNode<TInput, TOutput>? = null

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): AgentNode<TInput, TOutput> {
        if (node == null) {
            node = AgentNode(
                name = name ?: property.name,
                execute = execute
            )
        }
        return node!!
    }
}
