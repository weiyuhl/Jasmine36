package com.lhzkml.jasmine.core.agent.observe.snapshot

/**
 * 检查点过滤器接口
 * 完整移植 koog 的 AgentCheckpointPredicateFilter，
 * 用于在存储提供者中按条件筛选检查点。
 *
 * 可以按节点、时间、属性等自定义过滤逻辑。
 */
interface AgentCheckpointPredicateFilter {
    /**
     * 检查给定的检查点数据是否满足过滤条件
     *
     * @param checkpointData 要检查的检查点数据
     * @return true 表示满足条件，false 表示不满足
     */
    fun check(checkpointData: AgentCheckpoint): Boolean
}
