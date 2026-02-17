package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 多模型并行执行目标
 *
 * @param client 客户端实例
 * @param model 模型名称
 * @param label 显示标签（用于区分结果来源）
 */
data class ChatTarget(
    val client: ChatClient,
    val model: String,
    val label: String = "${client.provider.name}/$model"
)

/**
 * 多模型并行执行结果
 *
 * @param target 执行目标
 * @param result 成功时的结果，失败时为 null
 * @param error 失败时的异常，成功时为 null
 */
data class MultiChatResult(
    val target: ChatTarget,
    val result: ChatResult? = null,
    val error: Throwable? = null
) {
    val isSuccess: Boolean get() = result != null
    val content: String? get() = result?.content
}

/**
 * 多模型并行执行器
 *
 * 参考 koog 的 MultiLLMPromptExecutor 设计思路，扩展为支持将同一个 prompt
 * 同时发给多个模型并行执行。适用于模型对比、投票、A/B 测试等场景。
 *
 * koog 的 MultiLLMPromptExecutor 是按 provider 路由到单个客户端；
 * 本类是真正的并行执行——同一个 prompt 同时发给多个目标。
 *
 * 用法：
 * ```
 * val multi = MultiChatClient()
 * val results = multi.executeAll(
 *     messages = messages,
 *     targets = listOf(
 *         ChatTarget(openAIClient, "gpt-4o", "GPT-4o"),
 *         ChatTarget(claudeClient, "claude-sonnet-4-20250514", "Claude"),
 *     )
 * )
 * results.forEach { println("${it.target.label}: ${it.content}") }
 * ```
 *
 * 也可以从 ChatClientRouter 快速构建目标：
 * ```
 * val targets = multi.targetsFromRouter(router, mapOf("openai" to "gpt-4o", "claude" to "claude-sonnet"))
 * val results = multi.executeAll(messages, targets)
 * ```
 */
class MultiChatClient {

    /**
     * 从 ChatClientRouter 构建执行目标列表
     *
     * @param router 客户端路由器
     * @param providerModels 供应商 ID 到模型名的映射
     * @return 执行目标列表
     */
    fun targetsFromRouter(
        router: ChatClientRouter,
        providerModels: Map<String, String>
    ): List<ChatTarget> {
        return providerModels.mapNotNull { (providerId, model) ->
            router.getClient(providerId)?.let { client ->
                ChatTarget(client, model, "$providerId/$model")
            }
        }
    }

    /**
     * 并行执行所有目标，等待全部完成
     * 单个目标失败不影响其他目标，失败的结果会包含 error
     */
    suspend fun executeAll(
        messages: List<ChatMessage>,
        targets: List<ChatTarget>,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null
    ): List<MultiChatResult> = coroutineScope {
        targets.map { target ->
            async {
                try {
                    val result = target.client.chatWithUsage(
                        messages, target.model, maxTokens, samplingParams
                    )
                    MultiChatResult(target = target, result = result)
                } catch (e: Throwable) {
                    MultiChatResult(target = target, error = e)
                }
            }
        }.awaitAll()
    }

    /**
     * 并行执行，返回第一个成功的结果
     * 所有目标都失败时返回最后一个（包含 error）
     */
    suspend fun executeFirst(
        messages: List<ChatMessage>,
        targets: List<ChatTarget>,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null
    ): MultiChatResult {
        val results = executeAll(messages, targets, maxTokens, samplingParams)
        return results.firstOrNull { it.isSuccess }
            ?: results.last()
    }

    /**
     * 并行执行，只返回成功的结果
     */
    suspend fun executeSuccessful(
        messages: List<ChatMessage>,
        targets: List<ChatTarget>,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null
    ): List<MultiChatResult> {
        return executeAll(messages, targets, maxTokens, samplingParams).filter { it.isSuccess }
    }
}
