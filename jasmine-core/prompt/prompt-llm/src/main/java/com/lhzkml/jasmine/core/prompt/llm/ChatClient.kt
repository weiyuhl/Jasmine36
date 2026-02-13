package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.Usage
import kotlinx.coroutines.flow.Flow

/**
 * 流式聊天结果回调
 * 用于在流式输出完成后获取完整结果和用量信息
 */
data class StreamResult(
    val content: String,
    val usage: Usage? = null
)

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
     * 发送聊天请求（非流式），返回包含用量信息的结果
     * @param messages 消息列表
     * @param model 模型名称
     * @return ChatResult 包含回复内容和 token 用量
     */
    suspend fun chatWithUsage(messages: List<ChatMessage>, model: String): ChatResult

    /**
     * 发送聊天请求（流式）
     * @param messages 消息列表
     * @param model 模型名称
     * @return 逐块返回的文本内容 Flow
     */
    fun chatStream(messages: List<ChatMessage>, model: String): Flow<String>

    /**
     * 发送聊天请求（流式），完成后通过回调返回完整结果和用量
     * @param messages 消息列表
     * @param model 模型名称
     * @param onChunk 每个文本块的回调
     * @return StreamResult 包含完整回复和 token 用量
     */
    suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        onChunk: suspend (String) -> Unit
    ): StreamResult

    /**
     * 获取供应商可用的模型列表
     * @return 模型信息列表
     */
    suspend fun listModels(): List<ModelInfo>
}
