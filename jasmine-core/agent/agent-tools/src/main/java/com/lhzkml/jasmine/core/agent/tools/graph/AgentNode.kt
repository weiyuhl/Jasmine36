package com.lhzkml.jasmine.core.agent.tools.graph

/**
 * Agent 图节点
 * 参考 koog 的 AIAgentNode / AIAgentNodeBase，
 * 简化为适合 jasmine 的轻量实现。
 *
 * koog 的节点有 KType 泛型、边解析、执行上下文等复杂机制，
 * jasmine 简化为：节点有名字、执行函数、出边列表。
 *
 * @param name 节点名称（唯一标识）
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
     * 解析出边：找到第一个条件匹配的边
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
 * 起始节点 — 直接传递输入
 */
class StartNode<T>(subgraphName: String? = null) : AgentNode<T, T>(
    name = subgraphName?.let { "__start__$it" } ?: "__start__",
    execute = { input -> input }
)

/**
 * 结束节点 — 直接传递输入作为输出
 */
class FinishNode<T>(subgraphName: String? = null) : AgentNode<T, T>(
    name = subgraphName?.let { "__finish__$it" } ?: "__finish__",
    execute = { input -> input }
)
