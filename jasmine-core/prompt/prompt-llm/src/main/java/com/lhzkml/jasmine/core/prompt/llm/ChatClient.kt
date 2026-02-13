package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * 聊天客户端接口
 * 所有 LLM 供应商的客户端都需要实现此接口
 */
interface ChatClient : AutoCloseable {

    /** 供应商标识 */
    val provider: LLMProvider

    /**
     * 发送聊天请求（非流式）
     * @param messages 消息列表
     * @param model 模型名称
     * @return 助手回复的文本内容
     */
    suspend fun chat(messages: List<ChatMessage>, model: String): String

    /**
     * 发送聊天请求（流式）
     * @param messages 消息列表
     * @param model 模型名称
     * @return 逐块返回的文本内容 Flow
     */
    fun chatStream(messages: List<ChatMessage>, model: String): Flow<String>

    /**
     * 获取供应商可用的模型列表
     * @return 模型信息列表
     */
    suspend fun listModels(): List<ModelInfo>
}
