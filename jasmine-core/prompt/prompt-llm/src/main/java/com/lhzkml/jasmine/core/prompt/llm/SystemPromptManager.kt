package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage

/**
 * System Prompt 管理器
 * 负责系统提示词的管理逻辑，不依赖 Android 框架
 * 持久化由应用层（SharedPreferences / Room）负责
 */
class SystemPromptManager(
    /** 默认系统提示词 */
    var defaultPrompt: String = DEFAULT_PROMPT
) {

    companion object {
        const val DEFAULT_PROMPT = "You are a helpful assistant."

        /** 内置预设模板 */
        val presets = listOf(
            Preset("default", "默认助手", DEFAULT_PROMPT),
            Preset("translator", "翻译助手", "你是一个专业的翻译助手，擅长中英文互译。用户发送中文时翻译为英文，发送英文时翻译为中文。"),
            Preset("coder", "编程助手", "你是一个资深的编程助手，擅长多种编程语言。回答时提供清晰的代码示例和解释。"),
            Preset("writer", "写作助手", "你是一个专业的写作助手，擅长文章润色、改写和创作。注重文字的流畅性和表达力。"),
        )
    }

    /**
     * 预设模板
     */
    data class Preset(
        val id: String,
        val name: String,
        val prompt: String
    )

    /**
     * 创建 system 消息
     * @param customPrompt 自定义提示词，为 null 时使用默认值
     */
    fun createSystemMessage(customPrompt: String? = null): ChatMessage {
        val prompt = if (customPrompt.isNullOrBlank()) defaultPrompt else customPrompt
        return ChatMessage.system(prompt)
    }

    /**
     * 获取有效的 system prompt 文本
     * @param customPrompt 自定义提示词，为 null 或空时返回默认值
     */
    fun resolvePrompt(customPrompt: String? = null): String {
        return if (customPrompt.isNullOrBlank()) defaultPrompt else customPrompt
    }
}
