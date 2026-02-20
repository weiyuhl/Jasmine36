package com.lhzkml.jasmine.core.conversation.storage

import android.content.Context
import com.lhzkml.jasmine.core.conversation.storage.dao.UsageSummary
import com.lhzkml.jasmine.core.conversation.storage.entity.ConversationEntity
import com.lhzkml.jasmine.core.conversation.storage.entity.MessageEntity
import com.lhzkml.jasmine.core.conversation.storage.entity.UsageEntity
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 对话信息（用于 UI 展示）
 */
data class ConversationInfo(
    val id: String,
    val title: String,
    val providerId: String,
    val model: String,
    val systemPrompt: String,
    val workspacePath: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 带时间戳的消息（用于 UI 展示）
 */
data class TimedMessage(
    val role: String,
    val content: String,
    val createdAt: Long
)

/**
 * 对话仓库，提供对话和消息的增删改查
 * 这是框架层对外暴露的主要 API
 */
class ConversationRepository(context: Context) {

    private val dao = JasmineDatabase.getInstance(context).conversationDao()

    // ========== 对话管理 ==========

    /**
     * 创建新对话
     * @return 新对话的 ID
     */
    suspend fun createConversation(
        title: String,
        providerId: String,
        model: String,
        systemPrompt: String = "You are a helpful assistant.",
        workspacePath: String = ""
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertConversation(
            ConversationEntity(
                id = id,
                title = title,
                providerId = providerId,
                model = model,
                systemPrompt = systemPrompt,
                workspacePath = workspacePath,
                createdAt = now,
                updatedAt = now
            )
        )
        return id
    }

    /** 获取所有对话列表（实时观察） */
    fun observeConversations(): Flow<List<ConversationInfo>> {
        return dao.getAllConversations().map { list ->
            list.map { it.toInfo() }
        }
    }

    /** 获取指定工作区的对话列表（实时观察） */
    fun observeConversationsByWorkspace(workspacePath: String): Flow<List<ConversationInfo>> {
        return dao.getConversationsByWorkspace(workspacePath).map { list ->
            list.map { it.toInfo() }
        }
    }

    /** 获取单个对话信息 */
    suspend fun getConversation(id: String): ConversationInfo? {
        return dao.getConversation(id)?.toInfo()
    }

    /** 更新对话标题 */
    suspend fun updateTitle(conversationId: String, title: String) {
        val entity = dao.getConversation(conversationId) ?: return
        dao.updateConversation(entity.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    /** 更新对话的系统提示词 */
    suspend fun updateSystemPrompt(conversationId: String, systemPrompt: String) {
        dao.updateSystemPrompt(conversationId, systemPrompt, System.currentTimeMillis())
    }

    /** 获取对话的系统提示词 */
    suspend fun getSystemPrompt(conversationId: String): String? {
        return dao.getConversation(conversationId)?.systemPrompt
    }

    /** 删除对话（消息会级联删除） */
    suspend fun deleteConversation(id: String) {
        dao.deleteConversation(id)
    }

    /** 删除所有对话 */
    suspend fun deleteAllConversations() {
        dao.deleteAllConversations()
    }

    // ========== 消息管理 ==========

    /**
     * 添加一条消息到对话
     */
    suspend fun addMessage(conversationId: String, message: ChatMessage) {
        dao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = message.role,
                content = message.content,
                createdAt = System.currentTimeMillis()
            )
        )
        // 更新对话的最后修改时间
        val conversation = dao.getConversation(conversationId) ?: return
        dao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * 批量添加消息
     */
    suspend fun addMessages(conversationId: String, messages: List<ChatMessage>) {
        val now = System.currentTimeMillis()
        val entities = messages.mapIndexed { index, msg ->
            MessageEntity(
                conversationId = conversationId,
                role = msg.role,
                content = msg.content,
                createdAt = now + index // 保证顺序
            )
        }
        dao.insertMessages(entities)
        val conversation = dao.getConversation(conversationId) ?: return
        dao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    /** 获取对话的所有消息（转为 ChatMessage） */
    suspend fun getMessages(conversationId: String): List<ChatMessage> {
        return dao.getMessages(conversationId).map { it.toChatMessage() }
    }

    /** 获取对话的所有消息（带时间戳） */
    suspend fun getTimedMessages(conversationId: String): List<TimedMessage> {
        return dao.getMessages(conversationId).map {
            TimedMessage(role = it.role, content = it.content, createdAt = it.createdAt)
        }
    }

    /** 实时观察对话消息 */
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        return dao.observeMessages(conversationId).map { list ->
            list.map { it.toChatMessage() }
        }
    }

    // ========== 转换方法 ==========

    private fun ConversationEntity.toInfo() = ConversationInfo(
        id = id,
        title = title,
        providerId = providerId,
        model = model,
        systemPrompt = systemPrompt,
        workspacePath = workspacePath,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toChatMessage() = ChatMessage(
        role = role,
        content = content
    )

    // ========== 用量统计 ==========

    /**
     * 记录一次 API 调用的 token 用量
     */
    suspend fun recordUsage(
        conversationId: String,
        providerId: String,
        model: String,
        usage: Usage
    ) {
        dao.insertUsage(
            UsageEntity(
                conversationId = conversationId,
                providerId = providerId,
                model = model,
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                totalTokens = usage.totalTokens,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 获取某个对话的所有用量记录（按时间正序）
     * 用于恢复对话时匹配每条 assistant 消息的用量
     */
    suspend fun getUsageList(conversationId: String): List<Usage> {
        return dao.getUsageList(conversationId).map {
            Usage(promptTokens = it.promptTokens, completionTokens = it.completionTokens, totalTokens = it.totalTokens)
        }
    }

    /**
     * 获取某个对话的累计用量
     */
    suspend fun getConversationUsage(conversationId: String): UsageStats {
        return dao.getConversationUsage(conversationId).toStats()
    }

    /**
     * 获取全局累计用量
     */
    suspend fun getTotalUsage(): UsageStats {
        return dao.getTotalUsage().toStats()
    }

    /**
     * 实时观察全局累计用量
     */
    fun observeTotalUsage(): Flow<UsageStats> {
        return dao.observeTotalUsage().map { it.toStats() }
    }

    /**
     * 清除所有用量记录
     */
    suspend fun clearUsage() {
        dao.deleteAllUsage()
    }

    private fun UsageSummary.toStats() = UsageStats(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens
    )
}

/**
 * 用量统计信息（对外暴露的数据类）
 */
data class UsageStats(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
