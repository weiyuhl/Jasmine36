package com.lhzkml.jasmine.core.agent.tools.snapshot

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext
import com.lhzkml.jasmine.core.prompt.model.ChatMessage

/**
 * 持久化管理器
 * 完整移植 koog 的 Persistence feature，管理 Agent 执行过程中的检查点。
 *
 * 功能：
 * - 在节点执行后自动创建检查点
 * - 从检查点恢复 Agent 执行状态
 * - 支持回滚到之前的检查点
 *
 * @param provider 持久化存储提供者
 * @param autoCheckpoint 是否在每个节点执行后自动创建检查点
 */
class Persistence(
    private val provider: PersistenceStorageProvider<*>,
    private val autoCheckpoint: Boolean = true
) {
    private var currentVersion: Long = 0

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
     * 从检查点恢复 Agent 上下文
     * @param context 当前 Agent 上下文
     * @param checkpoint 要恢复的检查点
     */
    suspend fun restoreFromCheckpoint(
        context: AgentGraphContext,
        checkpoint: AgentCheckpoint
    ) {
        // 恢复消息历史到 session
        context.session.rewritePrompt { prompt ->
            prompt.withMessages { checkpoint.messageHistory }
        }
        // 存储恢复信息
        context.put("__restored_node__", checkpoint.nodePath)
        context.put("__restored_input__", checkpoint.lastInput)
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

    companion object {
        /** 禁用持久化 */
        val DISABLED = Persistence(NoPersistenceStorageProvider<Any>(), autoCheckpoint = false)
    }
}
