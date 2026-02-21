package com.lhzkml.jasmine.core.prompt.model

/**
 * 提示词数据类
 * 参考 koog 的 Prompt，封装消息列表 + 参数配置
 *
 * Prompt 是不可变的，每次修改都返回新实例。
 *
 * @param messages 消息列表
 * @param id 提示词标识
 * @param samplingParams 采样参数
 * @param maxTokens 最大回复 token 数，null 表示不限制
 * @param toolChoice 工具选择策略
 */
data class Prompt(
    val messages: List<ChatMessage>,
    val id: String,
    val samplingParams: SamplingParams = SamplingParams.DEFAULT,
    val maxTokens: Int? = null,
    val toolChoice: ToolChoice? = null
) {
    companion object {
        val Empty = Prompt(emptyList(), "")

        /**
         * DSL 构建 Prompt
         * ```kotlin
         * val prompt = Prompt.build("chat") {
         *     system("You are a helpful assistant.")
         *     user("Hello!")
         * }
         * ```
         */
        fun build(
            id: String,
            samplingParams: SamplingParams = SamplingParams.DEFAULT,
            maxTokens: Int? = null,
            init: PromptBuilder.() -> Unit
        ): Prompt {
            val builder = PromptBuilder(id, samplingParams, maxTokens)
            builder.init()
            return builder.build()
        }

        /**
         * 基于已有 Prompt 追加消息
         */
        fun build(prompt: Prompt, init: PromptBuilder.() -> Unit): Prompt {
            return PromptBuilder.from(prompt).also(init).build()
        }
    }

    /** 追加消息，返回新 Prompt */
    fun withMessages(update: (List<ChatMessage>) -> List<ChatMessage>): Prompt =
        copy(messages = update(messages))

    /** 更新采样参数 */
    fun withSamplingParams(params: SamplingParams): Prompt =
        copy(samplingParams = params)

    /** 更新工具选择策略 */
    fun withToolChoice(choice: ToolChoice?): Prompt =
        copy(toolChoice = choice)

    /** 更新最大 token 数 */
    fun withMaxTokens(max: Int?): Prompt =
        copy(maxTokens = max)

    /** 获取最后一条 assistant 消息的 token 用量 */
    val lastAssistantContent: String?
        get() = messages.lastOrNull { it.role == "assistant" }?.content
}

/**
 * 工具选择策略
 * 参考 koog 的 LLMParams.ToolChoice
 */
sealed class ToolChoice {
    /** LLM 自动决定是否调用工具 */
    data object Auto : ToolChoice()
    /** LLM 必须调用工具 */
    data object Required : ToolChoice()
    /** LLM 不允许调用工具 */
    data object None : ToolChoice()
    /** LLM 必须调用指定工具 */
    data class Named(val toolName: String) : ToolChoice()
}


/**
 * Message 类型的消息列表（惰性转换）
 * 移植自 koog 的类型化消息系统
 */
val Prompt.typedMessages: List<Message>
    get() = messages.map { it.toMessage() }

/**
 * 从 Message 列表构建 Prompt
 */
fun Prompt.Companion.fromMessages(
    messages: List<Message>,
    id: String,
    samplingParams: SamplingParams = SamplingParams.DEFAULT,
    maxTokens: Int? = null,
    toolChoice: ToolChoice? = null
): Prompt = Prompt(
    messages = messages.map { it.toChatMessage() },
    id = id,
    samplingParams = samplingParams,
    maxTokens = maxTokens,
    toolChoice = toolChoice
)
