package com.lhzkml.jasmine.core.prompt.mnn

/**
 * MNN 配置
 */
data class MnnConfig(
    val maxNewTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val systemPrompt: String = "You are a helpful assistant.",
    /** 是否启用深度思考模式（Thinking 模型如 Qwen3-Thinking 等） */
    val enableThinking: Boolean = true
) {
    fun toJson(): String {
        return """
            {
                "max_new_tokens": $maxNewTokens,
                "temperature": $temperature,
                "top_p": $topP,
                "top_k": $topK,
                "system_prompt": "$systemPrompt",
                "jinja": {
                    "context": {
                        "enable_thinking": $enableThinking
                    }
                }
            }
        """.trimIndent()
    }
}
