package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.BalanceInfo
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolChoice
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
    val toolCalls: List<ToolCall> = emptyList(),
    /** 思考/推理过程内容（Claude extended thinking 等） */
    val thinking: String? = null
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
        tools: List<ToolDescriptor> = emptyList(),
        toolChoice: ToolChoice? = null
    ): String

    /**
     * 发送聊天请求（非流式），返回包含用量信息的结果
     */
    suspend fun chatWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList(),
        toolChoice: ToolChoice? = null
    ): ChatResult

    /**
     * 发送聊天请求（流式）
     */
    fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList(),
        toolChoice: ToolChoice? = null
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
        toolChoice: ToolChoice? = null,
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

/**
 * 支持思考/推理内容实时回调的聊天客户端
 * 实现此接口的客户端可以在流式请求中实时回调思考内容
 */
interface ThinkingChatClient : ChatClient {
    /**
     * 发送聊天请求（流式），支持思考内容实时回调
     * @param onThinking 思考/推理内容的实时回调（Claude extended thinking / DeepSeek reasoning_content）
     */
    suspend fun chatStreamWithThinking(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList(),
        toolChoice: ToolChoice? = null,
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit
    ): StreamResult
}

/**
 * 扩展函数：如果客户端支持思考回调则使用，否则回退到普通流式
 */
suspend fun ChatClient.chatStreamWithUsageAndThinking(
    messages: List<ChatMessage>,
    model: String,
    maxTokens: Int? = null,
    samplingParams: SamplingParams? = null,
    tools: List<ToolDescriptor> = emptyList(),
    toolChoice: ToolChoice? = null,
    onChunk: suspend (String) -> Unit,
    onThinking: suspend (String) -> Unit = {}
): StreamResult {
    return if (this is ThinkingChatClient) {
        chatStreamWithThinking(messages, model, maxTokens, samplingParams, tools, toolChoice, onChunk, onThinking)
    } else {
        chatStreamWithUsage(messages, model, maxTokens, samplingParams, tools, toolChoice, onChunk)
    }
}
