package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.BalanceInfo
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.Usage
import kotlinx.coroutines.flow.Flow

/**
 * 流式聊天结果回调
 */
data class StreamResult(
    val content: String,
    val usage: Usage? = null,
    val finishReason: String? = null,
    val toolCalls: List<ToolCall> = emptyList()
) {
    /** 是否包含工具调用 */
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}

/**
 * 聊天客户端接口
 * 所有 LLM 供应商的客户端都需要实现此接口
 */
interface ChatClient : AutoCloseable {

    /** 供应商标识 */
    val provider: LLMProvider

    /**
     * 发送聊天请求（非流式）
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList()
    ): String

    /**
     * 发送聊天请求（非流式），返回包含用量信息的结果
     */
    suspend fun chatWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList()
    ): ChatResult

    /**
     * 发送聊天请求（流式）
     */
    fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList()
    ): Flow<String>

    /**
     * 发送聊天请求（流式），完成后返回完整结果和用量
     */
    suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList(),
        onChunk: suspend (String) -> Unit
    ): StreamResult

    /**
     * 获取供应商可用的模型列表
     */
    suspend fun listModels(): List<ModelInfo>

    /**
     * 查询账户余额（并非所有供应商都支持）
     */
    suspend fun getBalance(): BalanceInfo? = null
}
