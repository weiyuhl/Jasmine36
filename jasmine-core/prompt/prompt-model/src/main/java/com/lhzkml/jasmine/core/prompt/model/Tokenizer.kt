package com.lhzkml.jasmine.core.prompt.model

/**
 * Token 计数器接口
 * 完整移植 koog 的 Tokenizer 接口体系。
 *
 * 用于估算文本的 token 数量，对 LLM 调用的成本控制和上下文窗口管理至关重要。
 * 不同实现可以提供不同的精度和性能。
 */
interface Tokenizer {
    /**
     * 计算给定文本的 token 数量
     * @param text 要计数的文本
     * @return 估算的 token 数量
     */
    fun countTokens(text: String): Int

    /**
     * 计算单条消息的 token 数（含消息开销）
     * 每条消息有固定开销（role 标记、分隔符等）约 4 token
     *
     * @param role 消息角色
     * @param content 消息内容
     * @return 估算的 token 数
     */
    fun countMessageTokens(role: String, content: String): Int =
        MESSAGE_OVERHEAD + countTokens(role) + countTokens(content)

    companion object {
        /** 每条消息的固定 token 开销（role、分隔符等） */
        const val MESSAGE_OVERHEAD = 4
    }
}

/**
 * 空 Tokenizer — 始终返回 0
 * 参考 koog 的 NoTokenizer
 *
 * 当不需要 token 计数时使用，节省计算资源。
 */
class NoTokenizer : Tokenizer {
    override fun countTokens(text: String): Int = 0
}

/**
 * 基于正则的简单 Tokenizer
 * 参考 koog 的 SimpleRegexBasedTokenizer
 *
 * 按空白和常见标点分词，乘以 1.1 系数补偿特殊 token 和分词差异。
 * 对大多数 LLM 提供合理的近似值。
 */
class SimpleRegexBasedTokenizer : Tokenizer {
    override fun countTokens(text: String): Int {
        val tokens = text.split(Regex("\\s+|[,.;:!?()\\[\\]{}\"']+"))
            .filter { it.isNotEmpty() }
        return (tokens.size * 1.1).toInt()
    }
}

/**
 * Prompt Tokenizer 接口
 * 参考 koog 的 PromptTokenizer，提供消息级和 Prompt 级的 token 计数。
 */
interface PromptTokenizer {
    /** 计算单条消息的 token 数 */
    fun tokenCountFor(message: ChatMessage): Int

    /** 计算整个 Prompt 的 token 数 */
    fun tokenCountFor(prompt: Prompt): Int
}

/**
 * 按需 Tokenizer — 每次调用都重新计算
 * 参考 koog 的 OnDemandTokenizer
 */
class OnDemandPromptTokenizer(private val tokenizer: Tokenizer) : PromptTokenizer {
    override fun tokenCountFor(message: ChatMessage): Int =
        tokenizer.countTokens(message.content)

    override fun tokenCountFor(prompt: Prompt): Int =
        prompt.messages.sumOf { tokenCountFor(it) }
}

/**
 * 缓存 Tokenizer — 缓存已计算的 token 数，避免重复计算
 * 参考 koog 的 CachingTokenizer
 */
class CachingPromptTokenizer(private val tokenizer: Tokenizer) : PromptTokenizer {
    internal val cache = mutableMapOf<ChatMessage, Int>()

    override fun tokenCountFor(message: ChatMessage): Int =
        cache.getOrPut(message) { tokenizer.countTokens(message.content) }

    override fun tokenCountFor(prompt: Prompt): Int =
        prompt.messages.sumOf { tokenCountFor(it) }

    /** 清除缓存 */
    fun clearCache() {
        cache.clear()
    }
}
