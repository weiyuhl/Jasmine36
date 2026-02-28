package com.lhzkml.jasmine.core.agent.observe.snapshot

import com.lhzkml.jasmine.core.prompt.model.ChatMessage

/**
 * 持久化管理器
 * 完整移植 koog 的 Persistence feature，管理 Agent 执行过程中的检查点。
 *
 * 功能：
 * - 在节点执行后自动创建检查点
 * - 从检查点恢复 Agent 执行状态
 * - 支持回滚到之前的检查点
 * - 支持回滚工具注册（撤销有副作用的工具调用）
 *
 * @param provider 持久化存储提供者
 * @param autoCheckpoint 是否在每个节点执行后自动创建检查点
 */
class Persistence(
    private val provider: PersistenceStorageProvider<*>,
    private val autoCheckpoint: Boolean = true
) {
    companion object {
        /** 禁用持久化 */
        val DISABLED = Persistence(NoPersistenceStorageProvider(), autoCheckpoint = false)

        /**
         * 从检查点列表重建完整消息历史
         * 取 systemPrompt + 按顺序拼接每个检查点的 user/assistant 消息。
         *
         * @param checkpoints 按时间排序的检查点列表
         * @param systemPrompt 系统提示词
         * @return 重建的完整消息历史
         */
        fun rebuildHistoryFromCheckpoints(
            checkpoints: List<AgentCheckpoint>,
            systemPrompt: String
        ): List<ChatMessage> {
            val rebuilt = mutableListOf<ChatMessage>()
            rebuilt.add(ChatMessage.system(systemPrompt))
            for (cp in checkpoints) {
                rebuilt.addAll(cp.messageHistory)
            }
            return rebuilt
        }
    }

    private var currentVersion: Long = 0

    /**
     * 回滚策略
     * 参考 koog 的 RollbackStrategy
     */
    var rollbackStrategy: RollbackStrategy = RollbackStrategy.RESTART_FROM_NODE

    /**
     * 回滚工具注册表
     * 参考 koog 的 RollbackToolRegistry，用于管理有副作用的工具的回滚操作。
     */
    var rollbackToolRegistry: RollbackToolRegistry = RollbackToolRegistry.EMPTY

    /**
     * 创建检查点
     * @param agentId Agent ID
     * @param nodePath 当前节点路径
     * @param lastInput 最后一次输入
     * @param messageHistory 当前消息历史
     */
    suspend fun createCheckpoint(
        agentId: String,
        nodePath: String,
        lastInput: String?,
        messageHistory: List<ChatMessage>
    ): AgentCheckpoint {
        currentVersion++
        val checkpoint = AgentCheckpoint(
            checkpointId = java.util.UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            nodePath = nodePath,
            lastInput = lastInput,
            messageHistory = messageHistory,
            version = currentVersion
        )
        provider.saveCheckpoint(agentId, checkpoint)
        return checkpoint
    }

    /**
     * 获取最新检查点
     */
    suspend fun getLatestCheckpoint(agentId: String): AgentCheckpoint? =
        provider.getLatestCheckpoint(agentId)

    /**
     * 获取所有检查点
     */
    suspend fun getCheckpoints(agentId: String): List<AgentCheckpoint> =
        provider.getCheckpoints(agentId)

    /**
     * 根据 ID 获取检查点
     * 参考 koog 的 getCheckpointById
     */
    suspend fun getCheckpointById(agentId: String, checkpointId: String): AgentCheckpoint? {
        val allCps = provider.getCheckpoints(agentId)
        return allCps.firstOrNull { it.checkpointId == checkpointId }
    }

    /**
     * 标记 Agent 执行结束（创建墓碑检查点）
     */
    suspend fun markCompleted(agentId: String) {
        currentVersion++
        provider.saveCheckpoint(agentId, AgentCheckpoint.tombstone(currentVersion))
    }

    /**
     * 清除所有检查点
     */
    suspend fun clearCheckpoints(agentId: String) {
        provider.deleteCheckpoints(agentId)
    }

    /**
     * 删除单个检查点
     */
    suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        provider.deleteCheckpoint(agentId, checkpointId)
    }

    /**
     * 在节点执行后自动创建检查点（如果启用）
     */
    suspend fun onNodeCompleted(
        agentId: String,
        nodePath: String,
        lastInput: String?,
        messageHistory: List<ChatMessage>
    ) {
        if (autoCheckpoint) {
            createCheckpoint(agentId, nodePath, lastInput, messageHistory)
        }
    }

    /**
     * 计算消息历史差异
     * 完整移植 koog 的 messageHistoryDiff。
     *
     * 只在当前消息历史在检查点之后（时间前进方向）时有效，
     * 否则返回空列表。
     *
     * @param currentMessages 当前消息列表
     * @param checkpointMessages 检查点消息列表
     * @return 差异消息列表
     */
    internal fun messageHistoryDiff(
        currentMessages: List<ChatMessage>,
        checkpointMessages: List<ChatMessage>
    ): List<ChatMessage> {
        if (checkpointMessages.size > currentMessages.size) {
            return emptyList()
        }

        checkpointMessages.forEachIndexed { index, message ->
            if (currentMessages[index] != message) {
                return emptyList()
            }
        }

        return currentMessages.takeLast(currentMessages.size - checkpointMessages.size)
    }

}
