package com.lhzkml.jasmine.core.agent.runtime

import com.lhzkml.jasmine.core.agent.tools.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.tools.snapshot.FilePersistenceStorageProvider
import com.lhzkml.jasmine.core.agent.tools.snapshot.Persistence
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import java.io.File

/**
 * 检查点统计信息
 */
data class CheckpointStats(
    val totalSessions: Int,
    val totalCheckpoints: Int
)

/**
 * 会话检查点组
 */
data class SessionCheckpoints(
    val agentId: String,
    val checkpoints: List<AgentCheckpoint>
)

/**
 * 检查点服务接口
 *
 * 封装检查点的查询/删除/恢复操作，
 * 将 CheckpointManagerActivity 和 MainActivity 中的检查点业务逻辑迁移到 core 层。
 */
interface CheckpointService {

    /** 获取所有会话的检查点（按会话分组） */
    suspend fun listAllSessions(): List<SessionCheckpoints>

    /** 获取指定会话的检查点 */
    suspend fun getCheckpoints(agentId: String): List<AgentCheckpoint>

    /** 获取指定检查点 */
    suspend fun getCheckpoint(agentId: String, checkpointId: String): AgentCheckpoint?

    /** 删除指定会话的所有检查点 */
    suspend fun deleteSession(agentId: String)

    /** 删除指定检查点 */
    suspend fun deleteCheckpoint(agentId: String, checkpointId: String)

    /** 清除所有检查点 */
    suspend fun clearAll()

    /** 获取统计信息 */
    suspend fun getStats(): CheckpointStats

    /**
     * 从检查点重建消息历史
     *
     * @param agentId 会话 ID
     * @param upToCheckpointId 重建到哪个检查点（包含），null 表示全部
     * @param systemPrompt 系统提示词
     * @return 重建的完整消息历史
     */
    suspend fun rebuildHistory(
        agentId: String,
        upToCheckpointId: String? = null,
        systemPrompt: String
    ): List<ChatMessage>
}

/**
 * 基于文件系统的检查点服务实现
 *
 * @param snapshotDir 快照存储目录（如 `getExternalFilesDir("snapshots")`）
 */
class FileCheckpointService(private val snapshotDir: File) : CheckpointService {

    private val provider by lazy { FilePersistenceStorageProvider(snapshotDir) }

    override suspend fun listAllSessions(): List<SessionCheckpoints> {
        if (!snapshotDir.exists()) return emptyList()

        val agentDirs = snapshotDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val result = mutableListOf<SessionCheckpoints>()

        for (dir in agentDirs) {
            val agentId = dir.name
            val checkpoints = provider.getCheckpoints(agentId)
            if (checkpoints.isNotEmpty()) {
                result.add(SessionCheckpoints(
                    agentId = agentId,
                    checkpoints = checkpoints.sortedByDescending { it.createdAt }
                ))
            }
        }
        return result
    }

    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpoint> {
        return provider.getCheckpoints(agentId)
    }

    override suspend fun getCheckpoint(agentId: String, checkpointId: String): AgentCheckpoint? {
        return provider.getCheckpoints(agentId).firstOrNull { it.checkpointId == checkpointId }
    }

    override suspend fun deleteSession(agentId: String) {
        provider.deleteCheckpoints(agentId)
    }

    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        provider.deleteCheckpoint(agentId, checkpointId)
    }

    override suspend fun clearAll() {
        if (snapshotDir.exists()) {
            snapshotDir.deleteRecursively()
            snapshotDir.mkdirs()
        }
    }

    override suspend fun getStats(): CheckpointStats {
        val sessions = listAllSessions()
        return CheckpointStats(
            totalSessions = sessions.size,
            totalCheckpoints = sessions.sumOf { it.checkpoints.size }
        )
    }

    override suspend fun rebuildHistory(
        agentId: String,
        upToCheckpointId: String?,
        systemPrompt: String
    ): List<ChatMessage> {
        val allCheckpoints = provider.getCheckpoints(agentId).sortedBy { it.createdAt }
        if (allCheckpoints.isEmpty()) return emptyList()

        val relevantCheckpoints = if (upToCheckpointId != null) {
            val index = allCheckpoints.indexOfFirst { it.checkpointId == upToCheckpointId }
            if (index >= 0) allCheckpoints.take(index + 1) else allCheckpoints
        } else {
            allCheckpoints
        }

        return Persistence.rebuildHistoryFromCheckpoints(relevantCheckpoints, systemPrompt)
    }
}
