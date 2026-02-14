package com.lhzkml.jasmine.core.prompt.llm

/**
 * Token 计数器接口
 * 用于估算文本的 token 数量，支持可插拔实现
 */
interface Tokenizer {
    /**
     * 计算文本的 token 数
     * @param text 待计算的文本
     * @return 估算的 token 数
     */
    fun countTokens(text: String): Int

    /**
     * 计算单条消息的 token 数（含消息开销）
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
