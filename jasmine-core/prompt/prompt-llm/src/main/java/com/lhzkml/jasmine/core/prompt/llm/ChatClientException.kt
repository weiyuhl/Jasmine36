package com.lhzkml.jasmine.core.prompt.llm

/**
 * 聊天客户端异常
 * @param providerName 供应商名称
 * @param message 错误信息
 * @param cause 原始异常
 */
class ChatClientException(
    val providerName: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException("[$providerName] $message", cause)
