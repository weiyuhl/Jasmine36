package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor

/**
 * 流式输出超时续传助手
 *
 * 当流式输出因网络超时中断时，自动将已收到的部分内容保存，
 * 追加续传指令重新发起请求，让模型从断点处继续输出。
 *
 * @param maxResumes 最大续传次数，防止无限重试
 */
class StreamResumeHelper(
    private val maxResumes: Int = 3
) {

    companion object {
        /** 续传提示词：告诉模型从上次中断处继续 */
        private const val RESUME_PROMPT =
            "Your previous response was interrupted due to a network timeout. " +
            "The partial content received so far is shown above as an assistant message. " +
            "Please continue your response EXACTLY from where it was cut off. " +
            "Do NOT repeat any content that was already provided. " +
            "Do NOT add any preamble like \"Sure, continuing from where I left off\". " +
            "Just seamlessly continue the content."
    }

    /**
     * 带超时续传的流式输出
     *
     * @param client ChatClient 实例
     * @param messages 原始消息列表
     * @param model 模型名称
     * @param maxTokens 最大 token 数
     * @param samplingParams 采样参数
     * @param tools 工具列表
     * @param onChunk 每个 chunk 的回调
     * @param onThinking 思考内容回调
     * @param onResumeAttempt 续传尝试回调（用于 UI 提示），参数为第几次续传
     * @return StreamResult 完整的流式结果
     */
    suspend fun streamWithResume(
        client: ChatClient,
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int? = null,
        samplingParams: SamplingParams? = null,
        tools: List<ToolDescriptor> = emptyList(),
        onChunk: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit = {},
        onResumeAttempt: suspend (Int) -> Unit = {}
    ): StreamResult {
        val fullContent = StringBuilder()
        var lastResult: StreamResult? = null
        var currentMessages = messages

        for (attempt in 0..maxResumes) {
            try {
                val result = client.chatStreamWithUsageAndThinking(
                    currentMessages, model, maxTokens, samplingParams, tools,
                    onChunk = { chunk ->
                        fullContent.append(chunk)
                        onChunk(chunk)
                    },
                    onThinking = onThinking
                )

                // 正常完成，合并内容返回
                return StreamResult(
                    content = fullContent.toString(),
                    usage = result.usage,
                    finishReason = result.finishReason,
                    toolCalls = result.toolCalls,
                    thinking = result.thinking
                )
            } catch (e: ChatClientException) {
                if (e.errorType != ErrorType.NETWORK || attempt >= maxResumes) {
                    // 非网络错误或已达最大续传次数，直接抛出
                    throw e
                }

                val partialContent = fullContent.toString()
                if (partialContent.isEmpty()) {
                    // 还没收到任何内容就超时了，直接抛出让上层重试机制处理
                    throw e
                }

                // 有部分内容，尝试续传
                onResumeAttempt(attempt + 1)

                // 构建续传消息：原始消息 + 已收到的部分内容作为 assistant 消息 + 续传指令
                currentMessages = messages + listOf(
                    ChatMessage.assistant(partialContent),
                    ChatMessage.user(RESUME_PROMPT)
                )
            }
        }

        // 不应该到这里，但作为安全兜底
        return StreamResult(
            content = fullContent.toString(),
            usage = null,
            finishReason = "timeout_resumed"
        )
    }
}
