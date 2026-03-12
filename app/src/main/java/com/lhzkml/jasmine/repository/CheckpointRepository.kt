package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.agent.observe.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.runtime.CheckpointService
import com.lhzkml.jasmine.core.prompt.model.ChatMessage

/**
 * Checkpoint Repository
 *
 * 负责：
 * - 列出所有 session checkpoints
 * - 获取单会话 checkpoints
 * - 获取单 checkpoint 详情
 * - 删除 session
 * - 删除 checkpoint
 * - 清空全部
 * - 获取统计信息
 * - 执行恢复所需数据装配
 *
 * 对应页面：
 * - CheckpointManagerActivity
 * - CheckpointDetailActivity
 * - CheckpointRecovery.kt
 *
 * 说明：
 * - 这里不要让页面直接碰 CheckpointService
 * - 恢复逻辑也不要散落在 Activity 中
 */
interface CheckpointRepository {
    
    /**
     * 获取所有会话的 checkpoint 列表
     * @return Map<agentId, List<AgentCheckpoint>>
     */
    suspend fun getAllSessionCheckpoints(): Map<String, List<AgentCheckpoint>>
    
    /**
     * 获取指定会话的 checkpoints
     */
    suspend fun getCheckpoints(agentId: String): List<AgentCheckpoint>
    
    /**
     * 获取单个 checkpoint 详情
     */
    suspend fun getCheckpoint(agentId: String, checkpointId: String): AgentCheckpoint?
    
    /**
     * 删除指定会话的所有 checkpoints
     */
    suspend fun deleteSession(agentId: String)
    
    /**
     * 删除单个 checkpoint
     */
    suspend fun deleteCheckpoint(agentId: String, checkpointId: String)
    
    /**
     * 清空所有 checkpoints
     */
    suspend fun clearAll()
    
    /**
     * 获取统计信息
     */
    data class CheckpointStats(
        val totalSessions: Int,
        val totalCheckpoints: Int
    )
    
    suspend fun getStats(): CheckpointStats
    
    /**
     * 会话 Checkpoint 信息
     */
    data class SessionCheckpoints(
        val agentId: String,
        val checkpoints: List<AgentCheckpoint>
    )
    
    /**
     * 列出所有会话的 checkpoints
     */
    suspend fun listAllSessions(): List<SessionCheckpoints>
    
    /**
     * 重建历史消息
     * @param agentId 会话 ID
     * @param upToCheckpointId 恢复到的 checkpoint ID
     * @param systemPrompt 系统提示词
     * @return 重建的消息列表
     */
    suspend fun rebuildHistory(
        agentId: String,
        upToCheckpointId: String,
        systemPrompt: String
    ): List<ChatMessage>
}

class DefaultCheckpointRepository(
    private val checkpointService: CheckpointService?
) : CheckpointRepository {
    
    override suspend fun getAllSessionCheckpoints(): Map<String, List<AgentCheckpoint>> {
        val service = checkpointService ?: return emptyMap()
        return service.listAllSessions().associate { it.agentId to it.checkpoints }
    }
    
    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpoint> {
        val service = checkpointService ?: return emptyList()
        return service.getCheckpoints(agentId)
    }
    
    override suspend fun getCheckpoint(agentId: String, checkpointId: String): AgentCheckpoint? {
        val service = checkpointService ?: return null
        return service.getCheckpoint(agentId, checkpointId)
    }
    
    override suspend fun deleteSession(agentId: String) {
        val service = checkpointService ?: return
        service.deleteSession(agentId)
    }
    
    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        val service = checkpointService ?: return
        service.deleteCheckpoint(agentId, checkpointId)
    }
    
    override suspend fun clearAll() {
        val service = checkpointService ?: return
        service.clearAll()
    }
    
    override suspend fun getStats(): CheckpointRepository.CheckpointStats {
        val allSessions = checkpointService?.listAllSessions() ?: emptyList()
        val totalSessions = allSessions.size
        val totalCheckpoints = allSessions.sumOf { it.checkpoints.size }
        return CheckpointRepository.CheckpointStats(totalSessions, totalCheckpoints)
    }
    
    override suspend fun listAllSessions(): List<CheckpointRepository.SessionCheckpoints> {
        val service = checkpointService ?: return emptyList()
        return service.listAllSessions().map { session ->
            CheckpointRepository.SessionCheckpoints(
                agentId = session.agentId,
                checkpoints = session.checkpoints
            )
        }
    }
    
    override suspend fun rebuildHistory(
        agentId: String,
        upToCheckpointId: String,
        systemPrompt: String
    ): List<ChatMessage> {
        val service = checkpointService ?: return emptyList()
        return service.rebuildHistory(agentId, upToCheckpointId, systemPrompt)
    }
}
