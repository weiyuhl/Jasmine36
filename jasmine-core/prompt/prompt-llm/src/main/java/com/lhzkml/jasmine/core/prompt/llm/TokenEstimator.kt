package com.lhzkml.jasmine.core.prompt.llm

/**
 * 默认 Token 估算器
 * 不引入 tiktoken 等外部依赖，使用字符级近似估算
 *
 * 估算规则：
 * - CJK 字符（中日韩）：每个字符约 2 token（偏保守）
 * - ASCII 字符：每 4 个字符约 1 token
 * - 每条消息有固定开销（role 标记等）约 4 token
 */
object TokenEstimator : Tokenizer {

    /**
     * 估算单条文本的 token 数
     */
    override fun countTokens(text: String): Int {
        var tokens = 0
        var asciiCount = 0

        for (char in text) {
            if (isCJK(char)) {
                // 先结算累积的 ASCII
                tokens += (asciiCount + 3) / 4
                asciiCount = 0
                // CJK 字符按 2 token 算，偏保守避免超限
                tokens += 2
            } else {
                asciiCount++
            }
        }
        // 结算剩余 ASCII
        tokens += (asciiCount + 3) / 4

        return tokens
    }

    /**
     * 估算单条消息的 token 数（含开销）
     * 保留旧的静态方法签名以兼容
     */
    fun estimateMessage(role: String, content: String): Int =
        countMessageTokens(role, content)

    /**
     * 估算单条文本的 token 数
     * 保留旧的静态方法签名以兼容
     */
    fun estimate(text: String): Int = countTokens(text)

    private fun isCJK(char: Char): Boolean {
        val code = char.code
        return (code in 0x4E00..0x9FFF) ||   // CJK 统一汉字
                (code in 0x3400..0x4DBF) ||   // CJK 扩展 A
                (code in 0x3000..0x303F) ||   // CJK 标点
                (code in 0xFF00..0xFFEF) ||   // 全角字符
                (code in 0x3040..0x309F) ||   // 平假名
                (code in 0x30A0..0x30FF)      // 片假名
    }
}
