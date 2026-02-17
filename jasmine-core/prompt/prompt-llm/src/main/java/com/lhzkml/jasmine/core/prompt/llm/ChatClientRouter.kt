package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.BalanceInfo
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Fallback 配置
 *
 * 当请求的供应商未注册时，自动回退到指定的供应商和模型。
 *
 * @param providerId 回退供应商 ID
 * @param model 回退使用的模型名
 */
data class FallbackConfig(
    val providerId: String,
    val model: String
)

/**
 * 客户端路由器
 *
 * 参考 koog 的 MultiLLMPromptExecutor 设计，管理多个 ChatClient，
 * 根据供应商 ID 自动路由到对应客户端，支持 fallback 机制。
 *
 * 支持三种构造方式：
 * 1. 空构造 + register() 逐个注册
 * 2. vararg Pair 构造（id to client）
 * 3. vararg ChatClient 构造（自动按 provider.name 分组）
 *
 * 用法：
 * ```
 * // 方式一：逐个注册
 * val router = ChatClientRouter()
 * router.register("openai", openAIClient)
 * router.register("claude", claudeClient)
 *
 * // 方式二：Pair 构造
 * val router = ChatClientRouter("openai" to openAIClient, "claude" to claudeClient)
 *
 * // 方式三：自动分组
 * val router = ChatClientRouter.fromClients(openAIClient, claudeClient)
 *
 * // 带 fallback
 * val router = ChatClientRouter(
 *     "openai" to openAIClient,
 *     fallback = FallbackConfig("openai", "gpt-4o")
 * )
 *
 * // 路由请求
 * val result = router.chatWithUsage("openai", messages, "gpt-4o")
 * ```
 */
class ChatClientRouter(
    clients: Map<String, ChatClient> = emptyMap(),
    private val fallback: FallbackConfig? = null
) : AutoCloseable {

    private val clients = clients.toMutableMap()

    /**
     * vararg Pair 构造
     */
    constructor(
        vararg clients: Pair<String, ChatClient>,
        fallback: FallbackConfig? = null
    ) : this(clients.toMap(), fallback)

    init {
        if (fallback != null) {
            require(fallback.providerId in this.clients) {
                "Fallback 供应商 '${fallback.providerId}' 未注册"
            }
        }
    }

    companion object {
        /**
         * 从 ChatClient 列表自动构造，按 provider.name 作为 key
         */
        fun fromClients(vararg clients: ChatClient, fallback: FallbackConfig? = null): ChatClientRouter {
            val map = clients.associateBy { it.provider.name.lowercase() }
            return ChatClientRouter(map, fallback)
        }
    }

    /**
     * 注册客户端
     */
    fun register(id: String, client: ChatClient) {
        clients[id] = client
    }

    /**
     * 移除并关闭客户端
     */
    fun unregister(id: String) {
        clients.remove(id)?.close()
    }

    /**
     * 获取已注册的客户端
     */
    fun getClient(id: String): ChatClient? = clients[id]

    /**
     * 获取所有已注册的供应商 ID
     */
    fun registeredIds(): Set<String> = clients.keys.toSet()

    /**
     * 解析客户端：先精确匹配，再 fallback
     */
    private fun resolve(id: String): ResolvedClient {
        clients[id]?.let { return ResolvedClient(it, null) }

        if (fallback != null) {
            clients[fallback.providerId]?.let {
                return ResolvedClient(it, fallback.model)
            }
        }

        throw IllegalArgumentException("未找到供应商 '$id' 的客户端，且未设置 fallback")
    }

    /**
     * 解析结果，包含客户端和可能的 fallback 模型覆盖
     */
    private data class ResolvedClient(
        val client: ChatClient,
        val modelOverride: String?
    )

    // ========== 路由方法 ==========

    suspend fun chat(
        providerId: String,
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList()
    ): String {
        val resolved = resolve(providerId)
        return resolved.client.chat(messages, resolved.modelOverride ?: model, maxTokens, samplingParams, tools)
    }

    suspend fun chatWithUsage(
        providerId: String,
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList()
    ): ChatResult {
        val resolved = resolve(providerId)
        return resolved.client.chatWithUsage(messages, resolved.modelOverride ?: model, maxTokens, samplingParams, tools)
    }

    fun chatStream(
        providerId: String,
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList()
    ): Flow<String> {
        val resolved = resolve(providerId)
        return resolved.client.chatStream(messages, resolved.modelOverride ?: model, maxTokens, samplingParams, tools)
    }

    suspend fun chatStreamWithUsage(
        providerId: String,
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList(),
        onChunk: suspend (String) -> Unit
    ): StreamResult {
        val resolved = resolve(providerId)
        return resolved.client.chatStreamWithUsage(
            messages, resolved.modelOverride ?: model, maxTokens, samplingParams, tools, onChunk
        )
    }

    /**
     * 获取指定供应商的模型列表
     */
    suspend fun listModels(providerId: String): List<ModelInfo> =
        resolve(providerId).client.listModels()

    /**
     * 获取所有已注册供应商的模型列表
     */
    suspend fun listAllModels(): Map<String, List<ModelInfo>> {
        return clients.mapValues { (_, client) ->
            try { client.listModels() } catch (_: Exception) { emptyList() }
        }
    }

    /**
     * 查询余额
     */
    suspend fun getBalance(providerId: String): BalanceInfo? =
        resolve(providerId).client.getBalance()

    override fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}
