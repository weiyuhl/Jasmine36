package com.lhzkml.jasmine.mnn

import android.content.Context
import com.lhzkml.jasmine.ChatStopSignal
import com.lhzkml.jasmine.core.prompt.mnn.MnnChatClient as CoreMnnChatClient
import com.lhzkml.jasmine.core.prompt.mnn.MnnModelManager as CoreMnnModelManager
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.model.*
import com.lhzkml.jasmine.repository.ModelSelectionRepository
import kotlinx.coroutines.flow.Flow

/**
 * 应用层 MNN ChatClient 包装器
 * 委托给框架层的实现，添加应用层特定的功能（如 Thinking 配置）
 */
class MnnChatClient(
    private val context: Context,
    private val modelId: String,
    private val modelSelectionRepository: ModelSelectionRepository = 
        com.lhzkml.jasmine.config.AppConfig.modelSelectionRepository()
) : ChatClient {

    companion object {
        const val PROVIDER_ID = CoreMnnChatClient.PROVIDER_ID
    }

    private val coreClient = CoreMnnChatClient(
        context = context,
        modelId = modelId,
        stopSignalProvider = { ChatStopSignal.isRequested() }
    )

    override val provider: LLMProvider = coreClient.provider

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?
    ): String = coreClient.chat(messages, model, maxTokens, samplingParams, tools, toolChoice)

    override suspend fun chatWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?
    ): ChatResult = coreClient.chatWithUsage(messages, model, maxTokens, samplingParams, tools, toolChoice)

    override fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?
    ): Flow<String> = coreClient.chatStream(messages, model, maxTokens, samplingParams, tools, toolChoice)

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?,
        onChunk: suspend (String) -> Unit
    ): StreamResult = coreClient.chatStreamWithUsage(messages, model, maxTokens, samplingParams, tools, toolChoice, onChunk)

    override suspend fun listModels(): List<ModelInfo> = coreClient.listModels()

    override suspend fun getBalance(): BalanceInfo? = coreClient.getBalance()

    /**
     * 运行时切换 Thinking 模式（仅 Thinking 模型有效）
     */
    fun updateThinking(thinking: Boolean) {
        coreClient.updateThinking(thinking)
        modelSelectionRepository.setThinkingEnabled(modelId, thinking)
    }

    override fun close() {
        coreClient.close()
    }
}
