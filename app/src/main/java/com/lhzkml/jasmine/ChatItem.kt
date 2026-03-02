package com.lhzkml.jasmine

sealed class ChatItem {

    data class UserMessage(
        val content: String,
        val time: String
    ) : ChatItem()

    data class AiMessage(
        val blocks: List<ContentBlock> = emptyList(),
        val time: String = "",
        val usageLine: String = "",
        val isStreaming: Boolean = false
    ) : ChatItem()

    /** 等待模型回复时的动画占位（参考 Claude App 的 THINKING 态） */
    data object TypingIndicator : ChatItem()
}
