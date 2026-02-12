package com.lhzkml.jasmine.core

/**
 * 聊天客户端异常。
 * 参考 Koog 的 LLMClientException 设计。
 *
 * @property providerName 供应商名称
 */
class ChatClientException(
    val providerName: String,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException("[$providerName] $message", cause)
