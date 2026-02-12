package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage

/**
 * 聊天客户端接口
 * 所有 LLM 供应商的客户端都需要实现此接口
 */
interface ChatClient : AutoCloseable {

    /** 供应商标识 */
    val provider: LLMProvider

    /**
     * 发送聊天请求
     * @param messages 消息列表
     * @param model 模型名称
     * @return 助手回复的文本内容
     */
    suspend fun chat(messages: List<ChatMessage>, model: String): String
}
