package com.lhzkml.jasmine.core.agent.tools.graph

/**
 * Agent 图的有向边
 * 参考 koog 的 AIAgentEdge，连接两个节点，支持条件转发和数据变换。
 *
 * @param toNode 目标节点
 * @param condition 条件函数，返回 null 表示不匹配，返回值表示转换后的输入
 */
class AgentEdge<TFrom, TTo>(
    val toNode: AgentNode<TTo, *>,
    private val condition: suspend AgentGraphContext.(output: TFrom) -> TTo?
) {
    /**
     * 尝试转发：如果条件匹配返回转换后的值，否则返回 null
     */
    suspend fun tryForward(context: AgentGraphContext, output: TFrom): TTo? {
        return condition(context, output)
    }
}
