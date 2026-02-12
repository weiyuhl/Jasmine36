package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.Serializable

/**
 * 聊天消息
 * @param role 角色：system / user / assistant
 * @param content 消息内容
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        /** 创建系统消息 */
        fun system(content: String) = ChatMessage("system", content)

        /** 创建用户消息 */
        fun user(content: String) = ChatMessage("user", content)

        /** 创建助手消息 */
        fun assistant(content: String) = ChatMessage("assistant", content)
    }
}
