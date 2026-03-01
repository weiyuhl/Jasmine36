package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ActiveProviderConfig
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.viewmodel.MessageSendResult
import com.lhzkml.jasmine.viewmodel.MessageStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * 消息仓库接口
 * 
 * 负责消息发送、流式输出、Agent 执行等业务逻辑
 */
interface MessageRepository {
    
    /**
     * 发送消息（流式）
     * 
     * @param message 用户消息
     * @param conversationId 对话 ID（null 表示新对话）
     * @param config 供应商配置
     * @param messageHistory 消息历史
     * @return 消息流事件
     */
    fun sendMessageStream(
        message: String,
        conversationId: String?,
        config: ActiveProviderConfig,
        messageHistory: List<ChatMessage>
    ): Flow<MessageStreamEvent>
    
    /**
     * 取消当前消息发送
     */
    fun cancelSending()
    
    /**
     * 压缩历史消息
     * 
     * @param conversationId 对话 ID
     * @param messageHistory 消息历史
     * @return 压缩后的消息历史
     */
    suspend fun compressHistory(
        conversationId: String,
        messageHistory: List<ChatMessage>
    ): Result<List<ChatMessage>>
}
