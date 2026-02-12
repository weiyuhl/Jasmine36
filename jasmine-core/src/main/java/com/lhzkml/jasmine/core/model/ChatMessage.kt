package com.lhzkml.jasmine.core.model

import kotlinx.serialization.Serializable

/**
 * 聊天消息，对应 OpenAI 兼容 API 的 message 结构。
 *
 * @property role 消息角色：system / user / assistant
 * @property content 消息文本内容
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        /** 系统提示消息 */
        fun system(content: String) = ChatMessage(role = "system", content = content)

        /** 用户消息 */
        fun user(content: String) = ChatMessage(role = "user", content = content)

        /** 助手回复消息 */
        fun assistant(content: String) = ChatMessage(role = "assistant", content = content)
    }
}
