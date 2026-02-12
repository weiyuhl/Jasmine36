package com.lhzkml.jasmine.core.api

import com.lhzkml.jasmine.core.model.ChatMessage
import com.lhzkml.jasmine.core.provider.LLMProvider

/**
 * LLM 聊天客户端接口。
 * 参考 Koog 的 LLMClient 设计，定义与 LLM 通信的标准方法。
 *
 * 所有供应商客户端都需要实现此接口。
 * 实现 AutoCloseable，使用完毕后需要关闭释放资源。
 */
interface ChatClient : AutoCloseable {

    /** 供应商标识 */
    val provider: LLMProvider

    /**
     * 发送聊天请求，返回助手回复文本。
     *
     * @param messages 消息列表（system / user / assistant）
     * @param model 模型名称
     * @param temperature 温度参数，控制回复随机性
     * @return 助手回复的文本内容
     * @throws com.lhzkml.jasmine.core.exception.ChatClientException 请求失败时抛出
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double = 0.7
    ): String
}
