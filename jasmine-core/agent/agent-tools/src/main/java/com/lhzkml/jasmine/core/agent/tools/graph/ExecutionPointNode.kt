package com.lhzkml.jasmine.core.agent.tools.graph

/**
 * 执行点节点接口
 * 移植自 koog 的 ExecutionPointNode，支持 checkpoint/rollback 场景。
 *
 * 子图执行中可以跳转到任意节点重新执行，用于：
 * - 错误恢复：工具执行失败后回退到之前的节点
 * - 条件重试：根据 LLM 响应决定是否回退
 * - 分支跳转：动态改变执行路径
 *
 * 使用方式：
 * ```kotlin
 * // 在节点执行中设置强制执行点
 * val nodeCheck by node<ChatResult, ChatResult>("check") { result ->
 *     if (result.content.contains("error")) {
 *         // 回退到 nodeCallLLM 重新执行
 *         (subgraph as ExecutionPointNode).enforceExecutionPoint(nodeCallLLM, "Please try again")
 *     }
 *     result
 * }
 * ```
 */
interface ExecutionPointNode {
    /**
     * 获取当前强制执行点
     * 如果没有设置强制执行点，返回 null。
     *
     * @return 执行点（包含目标节点和可选输入），或 null
     */
    fun getExecutionPoint(): ExecutionPoint?

    /**
     * 重置强制执行点
     * 清除当前设置的强制节点和输入，恢复默认执行行为。
     */
    fun resetExecutionPoint()

    /**
     * 设置强制执行点
     * 下次迭代将跳转到指定节点执行，而非沿着正常的边转发。
     *
     * @param node 目标节点
     * @param input 可选的输入数据，传递给目标节点
     * @throws IllegalStateException 如果已经设置了强制执行点
     */
    fun enforceExecutionPoint(node: AgentNode<*, *>, input: Any? = null)
}

/**
 * 执行点
 * 移植自 koog 的 ExecutionPoint，表示图中的一个执行位置。
 *
 * @param node 目标节点
 * @param input 可选的输入数据
 */
data class ExecutionPoint(
    val node: AgentNode<*, *>,
    val input: Any? = null
)
