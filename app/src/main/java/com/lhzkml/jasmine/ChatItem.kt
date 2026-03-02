package com.lhzkml.jasmine

sealed class ChatItem {

    data class UserMessage(
        val content: String,
        val time: String
    ) : ChatItem()

    data class AiMessage(
        val content: String,
        val time: String = "",
        val usageLine: String = "",
        val isStreaming: Boolean = false
    ) : ChatItem()

    data class LogMessage(
        val content: String
    ) : ChatItem()
}
