package com.lhzkml.jasmine.core.agent.tools.snapshot

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext
import com.lhzkml.jasmine.core.agent.tools.graph.AgentStorageKey
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
        val KEY_RESTORED_NODE = AgentStorageKey<String>("__restored_node__")
        val KEY_RESTORED_INPUT = AgentStorageKey<String>("__restored_input__")

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
        context.storage.set(KEY_RESTORED_NODE, checkpoint.nodePath)
        checkpoint.lastInput?.let { context.storage.set(KEY_RESTORED_INPUT, it) }
    }

    /**
     * 回滚到指定检查点
     * 完整移植 koog 的 rollbackToCheckpoint，支持回滚工具执行。
     *
     * 注意：如果某些工具有副作用，需要通过 [RollbackToolRegistry] 注册回滚工具。
     * 回滚只在时间倒退方向有效（当前状态在检查点之后）。
     *
     * @param checkpointId 要回滚到的检查点 ID
     * @param context Agent 上下文
     * @return 恢复的检查点数据，如果未找到则返回 null
     */
    suspend fun rollbackToCheckpoint(
        checkpointId: String,
        context: AgentGraphContext
    ): AgentCheckpoint? {
        val checkpoint = getCheckpointById(context.agentId, checkpointId) ?: return null

        // 执行回滚工具（撤销副作用）
        val currentMessages = context.session.prompt.messages
        val diffMessages = messageHistoryDiff(currentMessages, checkpoint.messageHistory)

        // 找出差异中的工具调用，逆序执行回滚
        diffMessages
            .filter { it.role == "assistant" && !it.toolCalls.isNullOrEmpty() }
            .reversed()
            .flatMap { it.toolCalls ?: emptyList() }
            .forEach { toolCall ->
                rollbackToolRegistry.getRollbackTool(toolCall.name)?.let { rollbackEntry ->
                    try {
                        rollbackEntry.rollbackExecutor(toolCall.arguments)
                    } catch (_: Exception) {
                        // 回滚失败不应阻止恢复
                    }
                }
            }

        // 恢复检查点状态
        restoreFromCheckpoint(context, checkpoint)
        return checkpoint
    }

    /**
     * 回滚到最新检查点
     * 完整移植 koog 的 rollbackToLatestCheckpoint
     *
     * @param context Agent 上下文
     * @return 恢复的检查点数据，如果没有非墓碑检查点则返回 null
     */
    suspend fun rollbackToLatestCheckpoint(
        context: AgentGraphContext
    ): AgentCheckpoint? {
        val checkpoint = getLatestCheckpoint(context.agentId) ?: return null
        if (checkpoint.isTombstone()) return null

        restoreFromCheckpoint(context, checkpoint)
        return checkpoint
    }

    /**
     * 设置执行点
     * 完整移植 koog 的 setExecutionPoint，直接修改 Agent 上下文到指定状态。
     *
     * @param context Agent 上下文
     * @param nodePath 节点路径
     * @param messageHistory 消息历史
     * @param input 输入数据
     */
    suspend fun setExecutionPoint(
        context: AgentGraphContext,
        nodePath: String,
        messageHistory: List<ChatMessage>,
        input: String?
    ) {
        context.session.rewritePrompt { prompt ->
            prompt.withMessages { messageHistory }
        }
        context.storage.set(KEY_RESTORED_NODE, nodePath)
        input?.let { context.storage.set(KEY_RESTORED_INPUT, it) }
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
    private fun messageHistoryDiff(
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
