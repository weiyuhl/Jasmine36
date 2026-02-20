package com.lhzkml.jasmine.core.prompt.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * 重试配置
 */
data class RetryConfig(
    /** 最大重试次数 */
    val maxRetries: Int = 3,
    /** 初始延迟（毫秒） */
    val initialDelayMs: Long = 1000,
    /** 最大延迟（毫秒） */
    val maxDelayMs: Long = 10000,
    /** 退避倍数 */
    val backoffMultiplier: Double = 2.0,
    /** 请求超时（毫秒），整个请求的总时长上限 */
    val requestTimeoutMs: Long = 600000,
    /** 连接超时（毫秒） */
    val connectTimeoutMs: Long = 30000,
    /** Socket 读取超时（毫秒），流式输出时两个 chunk 之间的最大等待时间 */
    val socketTimeoutMs: Long = 300000
) {
    companion object {
        val DEFAULT = RetryConfig()
    }
}

/**
 * 带重试的执行器
 * 使用指数退避策略
 */
suspend fun <T> executeWithRetry(
    config: RetryConfig = RetryConfig.DEFAULT,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    
    repeat(config.maxRetries + 1) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            // 协程取消不重试，直接传播
            throw e
        } catch (e: ChatClientException) {
            lastException = e
            
            // 不可重试的错误直接抛出
            if (!e.isRetryable || attempt >= config.maxRetries) {
                throw e
            }
            
            // 计算延迟时间（指数退避）
            val delayMs = min(
                config.initialDelayMs * config.backoffMultiplier.pow(attempt).toLong(),
                config.maxDelayMs
            )
            
            delay(delayMs)
        } catch (e: Exception) {
            lastException = e
            
            // 其他异常也尝试重试（可能是网络问题）
            if (attempt >= config.maxRetries) {
                throw e
            }
            
            val delayMs = min(
                config.initialDelayMs * config.backoffMultiplier.pow(attempt).toLong(),
                config.maxDelayMs
            )
            
            delay(delayMs)
        }
    }
    
    throw lastException ?: RuntimeException("重试失败")
}
