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
}
