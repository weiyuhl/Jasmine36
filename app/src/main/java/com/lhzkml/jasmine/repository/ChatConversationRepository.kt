package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository as CoreConversationRepository
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * 对话 Repository (App 层)
 *
 * 负责：
 * - 创建对话
 * - 加载对话
 * - 删除对话
 * - 监听对话列表
 * - 读写消息历史
 *
 * 对应页面/功能：
 * - ChatViewModel
 * - ConversationViewModel
 * - DrawerContent
 *
 * 说明：
 * - 推荐用 app 层包装 core 的 ConversationRepository
 * - 不建议直接在 ViewModel/Activity 里 new core ConversationRepository
 */
interface ChatConversationRepository {
    
    /**
     * 创建新对话
     */
    suspend fun createConversation(
        title: String,
        providerId: String,
        model: String,
        systemPrompt: String = "You are a helpful assistant.",
        workspacePath: String = ""
    ): String
    
    /**
     * 获取对话信息
     */
    suspend fun getConversation(conversationId: String): ConversationInfo?
    
    /**
     * 监听对话列表变化
     */
    fun observeConversations(): Flow<List<ConversationInfo>>
    
    /**
     * 删除对话
     */
    suspend fun deleteConversation(conversationId: String)
    
    /**
     * 更新对话标题
     */
    suspend fun updateTitle(conversationId: String, title: String)
    
    /**
     * 添加单条消息
     */
    suspend fun addMessage(conversationId: String, message: ChatMessage)
    
    /**
     * 批量添加消息
     */
    suspend fun addMessages(conversationId: String, messages: List<ChatMessage>)
}

/**
 * 默认实现，委托给 core 层的 ConversationRepository
 */
class DefaultChatConversationRepository(
    private val coreRepo: CoreConversationRepository
) : ChatConversationRepository {
    
    override suspend fun createConversation(
        title: String,
        providerId: String,
        model: String,
        systemPrompt: String,
        workspacePath: String
    ): String {
        return coreRepo.createConversation(title, providerId, model, systemPrompt, workspacePath)
    }
    
    override suspend fun getConversation(conversationId: String): ConversationInfo? {
        return coreRepo.getConversation(conversationId)
    }
    
    override fun observeConversations(): Flow<List<ConversationInfo>> {
        return coreRepo.observeConversations()
    }
    
    override suspend fun deleteConversation(conversationId: String) {
        coreRepo.deleteConversation(conversationId)
    }
    
    override suspend fun updateTitle(conversationId: String, title: String) {
        coreRepo.updateTitle(conversationId, title)
    }
    
    override suspend fun addMessage(conversationId: String, message: ChatMessage) {
        coreRepo.addMessage(conversationId, message)
    }
    
    override suspend fun addMessages(conversationId: String, messages: List<ChatMessage>) {
        coreRepo.addMessages(conversationId, messages)
    }
}
