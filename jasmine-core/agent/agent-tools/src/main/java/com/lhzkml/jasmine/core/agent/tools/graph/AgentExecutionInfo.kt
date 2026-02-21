package com.lhzkml.jasmine.core.agent.tools.graph

import kotlinx.serialization.Serializable

/**
 * Agent 执行路径的默认分隔符
 * 移植自 koog 的 DEFAULT_AGENT_PATH_SEPARATOR
 */
const val DEFAULT_AGENT_PATH_SEPARATOR: String = "/"

/**
 * 将给定的部分拼接为路径字符串
 * 移植自 koog 的 AgentNodePath.path()
 *
 * @param parts 路径各部分
 * @param separator 分隔符，默认为 [DEFAULT_AGENT_PATH_SEPARATOR]
 * @return 拼接后的路径字符串
 */
fun path(vararg parts: String, separator: String = DEFAULT_AGENT_PATH_SEPARATOR): String {
    return parts.joinToString(separator)
}

/**
 * Agent 执行信息，表示执行路径中的一个节点
 * 移植自 koog 的 AgentExecutionInfo
 *
 * 通过 parent 链构成层级结构，可以生成完整的执行路径字符串。
 *
 * @property parent 父执行信息，null 表示根节点
 * @property partName 当前执行部分的名称
 */
@Serializable
data class AgentExecutionInfo(
    val parent: AgentExecutionInfo?,
    val partName: String
) {
    /**
     * 构建从根到当前节点的完整路径字符串
     * 移植自 koog 的 AgentExecutionInfo.path()
     *
     * @param separator 分隔符，null 时使用默认分隔符
     * @return 路径字符串
     */
    fun path(separator: String? = null): String {
        val sep = separator ?: DEFAULT_AGENT_PATH_SEPARATOR

        return buildList {
            var current: AgentExecutionInfo? = this@AgentExecutionInfo

            while (current != null) {
                add(current.partName)
                current = current.parent
            }
        }.reversed().joinToString(sep)
    }
}
