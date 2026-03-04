package com.lhzkml.jasmine.mnn

import android.content.Context
import android.util.Log
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.model.BalanceInfo
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolChoice
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地 MNN 模型的 ChatClient 实现。
 * 将 MnnLlmSession 适配为 ChatClient 接口，使本地推理能融入现有聊天流程。
 */
class MnnChatClient(
    private val context: Context,
    private val modelId: String
) : ChatClient {

    companion object {
        private const val TAG = "MnnChatClient"
        const val PROVIDER_ID = "mnn_local"
    }

    override val provider: LLMProvider = LLMProvider.Custom("MNN Local")

    private var session: MnnLlmSession? = null

    private fun ensureSession(model: String): MnnLlmSession {
        session?.let { return it }

        val models = MnnModelManager.getLocalModels(context)
        val targetId = model.ifEmpty { modelId }
        val modelInfo = models.find { it.modelId == targetId }
            ?: throw IllegalStateException("本地模型 $targetId 不存在，请先下载")

        val defaults = MnnModelManager.getGlobalDefaults(context) ?: MnnModelManager.defaultGlobalConfig()
        val mnnConfig = MnnConfig(
            maxNewTokens = defaults.maxNewTokens ?: 2048,
            temperature = defaults.temperature ?: 0.6f,
            topP = defaults.topP ?: 0.95f,
            topK = defaults.topK ?: 20,
            systemPrompt = ""
        )

        val newSession = MnnLlmSession(modelInfo.modelPath, mnnConfig)
        if (!newSession.init()) {
            throw IllegalStateException("MNN 模型初始化失败，请检查模型文件是否完整")
        }
        session = newSession
        return newSession
    }

    private fun buildPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            when (msg.role) {
                "system" -> {}
                "user" -> sb.appendLine("<|user|>\n${msg.content}")
                "assistant" -> sb.appendLine("<|assistant|>\n${msg.content}")
            }
        }
        sb.appendLine("<|assistant|>")
        return sb.toString()
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?
    ): String = withContext(Dispatchers.IO) {
        val s = ensureSession(model)
        val prompt = buildPrompt(messages)
        s.generate(prompt)
    }

    override suspend fun chatWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?
    ): ChatResult = withContext(Dispatchers.IO) {
        val s = ensureSession(model)
        val prompt = buildPrompt(messages)
        val result = s.generate(prompt)
        ChatResult(content = result, finishReason = "stop")
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?
    ): Flow<String> = callbackFlow {
        launch(Dispatchers.IO) {
            val s = ensureSession(model)
            val prompt = buildPrompt(messages)
            s.generate(prompt) { token ->
                trySend(token)
                false
            }
            close()
        }
        awaitClose()
    }

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: SamplingParams?,
        tools: List<ToolDescriptor>,
        toolChoice: ToolChoice?,
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        val s = withContext(Dispatchers.IO) { ensureSession(model) }
        val prompt = buildPrompt(messages)
        val fullResult = StringBuilder()
        withContext(Dispatchers.IO) {
            s.generate(prompt) { token ->
                fullResult.append(token)
                kotlinx.coroutines.runBlocking { onChunk(token) }
                false
            }
        }
        return StreamResult(
            content = fullResult.toString(),
            finishReason = "stop"
        )
    }

    override suspend fun listModels(): List<ModelInfo> {
        val models = MnnModelManager.getLocalModels(context)
        return models.map { info ->
            ModelInfo(
                id = info.modelId,
                ownedBy = "local",
                displayName = info.modelName,
                description = "本地 MNN 模型 (${MnnModelManager.formatSize(info.sizeBytes)})"
            )
        }
    }

    override suspend fun getBalance(): BalanceInfo? = null

    override fun close() {
        try {
            session?.release()
            session = null
            Log.d(TAG, "MnnChatClient closed for model $modelId")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session", e)
        }
    }
}
