package com.lhzkml.jasmine.core.prompt.llm

/**
 * 错误类型枚举
 */
enum class ErrorType {
    /** 网络错误（连接超时、DNS 解析失败等） */
    NETWORK,
    /** 认证失败（API Key 无效、过期等） */
    AUTHENTICATION,
    /** 限流（请求过于频繁） */
    RATE_LIMIT,
    /** 模型不可用（模型名称错误、模型维护中等） */
    MODEL_UNAVAILABLE,
    /** 请求参数错误（max_tokens 超限、消息格式错误等） */
    INVALID_REQUEST,
    /** 服务器错误（5xx） */
    SERVER_ERROR,
    /** 响应解析错误 */
    PARSE_ERROR,
    /** 未知错误 */
    UNKNOWN
}

/**
 * 聊天客户端异常
 * @param providerName 供应商名称
 * @param message 错误信息
 * @param errorType 错误类型
 * @param statusCode HTTP 状态码（如果有）
 * @param cause 原始异常
 */
class ChatClientException(
    val providerName: String,
    message: String,
    val errorType: ErrorType = ErrorType.UNKNOWN,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : RuntimeException("[$providerName] $message", cause) {

    /** 是否可重试 */
    val isRetryable: Boolean
        get() = when (errorType) {
            ErrorType.NETWORK, ErrorType.RATE_LIMIT, ErrorType.SERVER_ERROR -> true
            else -> false
        }

    companion object {
        /**
         * 根据 HTTP 状态码创建异常
         */
        fun fromStatusCode(providerName: String, statusCode: Int, responseBody: String? = null): ChatClientException {
            val (errorType, message) = when (statusCode) {
                401 -> ErrorType.AUTHENTICATION to "API Key 无效或已过期"
                403 -> ErrorType.AUTHENTICATION to "无权访问该资源"
                429 -> ErrorType.RATE_LIMIT to "请求过于频繁，请稍后重试"
                400 -> ErrorType.INVALID_REQUEST to "请求参数错误: ${responseBody ?: "未知"}"
                404 -> ErrorType.MODEL_UNAVAILABLE to "模型不存在或不可用"
                500, 502, 503, 504 -> ErrorType.SERVER_ERROR to "服务器错误，请稍后重试"
                else -> ErrorType.UNKNOWN to "请求失败，状态码: $statusCode"
            }
            return ChatClientException(providerName, message, errorType, statusCode)
        }
    }
}
