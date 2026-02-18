package com.lhzkml.jasmine.core.agent.tools.snapshot

import com.lhzkml.jasmine.core.prompt.model.ChatMessage

/**
 * Agent 检查点数据
 * 完整移植 koog 的 AgentCheckpointData，保存 Agent 执行状态的快照。
 *
 * 用于：
 * - 保存 Agent 执行进度（app 被杀后恢复）
 * - 回滚到之前的状态
 * - 调试和审计
 *
 * @param checkpointId 检查点唯一 ID
 * @param createdAt 创建时间戳（毫秒）
 * @param nodePath 当前执行到的节点路径
 * @param lastInput 最后一次节点输入（序列化为字符串）
 * @param messageHistory 到检查点为止的消息历史
 * @param version 检查点数据版本号
 * @param properties 附加属性（键值对）
 */
data class AgentCheckpoint(
    val checkpointId: String,
    val createdAt: Long,
    val nodePath: String,
    val lastInput: String?,
    val messageHistory: List<ChatMessage>,
    val version: Long,
    val properties: Map<String, String>? = null
) {
    /** 是否为墓碑检查点（标记已终止的会话） */
    fun isTombstone(): Boolean =
        properties?.get(PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME) == "true"

    companion object {
        /** 创建墓碑检查点 */
        fun tombstone(version: Long): AgentCheckpoint = AgentCheckpoint(
            checkpointId = java.util.UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            nodePath = PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME,
            lastInput = null,
            messageHistory = emptyList(),
            version = version,
            properties = mapOf(PersistenceUtils.TOMBSTONE_CHECKPOINT_NAME to "true")
        )
    }
}

/**
 * 回滚策略
 * 参考 koog 的 RollbackStrategy
 */
enum class RollbackStrategy {
    /** 回滚到检查点并从该节点重新执行 */
    RESTART_FROM_NODE,
    /** 回滚到检查点并跳过该节点 */
    SKIP_NODE,
    /** 回滚到检查点并使用默认输出 */
    USE_DEFAULT_OUTPUT
}
