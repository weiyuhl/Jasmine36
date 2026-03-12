package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * 超时与续传设置 Repository
 *
 * 负责：
 * - Request Timeout
 * - Socket Timeout
 * - Connect Timeout
 * - Stream Resume 开关与重试次数
 *
 * 对应页面：
 * - TimeoutConfigActivity
 * - ChatExecutor 中超时读取
 */
interface TimeoutSettingsRepository {
    
    // Request Timeout
    fun getRequestTimeout(): Int
    fun setRequestTimeout(seconds: Int)
    
    // Socket Timeout
    fun getSocketTimeout(): Int
    fun setSocketTimeout(seconds: Int)
    
    // Connect Timeout
    fun getConnectTimeout(): Int
    fun setConnectTimeout(seconds: Int)
    
    // Stream Resume
    fun isStreamResumeEnabled(): Boolean
    fun setStreamResumeEnabled(enabled: Boolean)
    fun getStreamResumeMaxRetries(): Int
    fun setStreamResumeMaxRetries(retries: Int)
}

/**
 * 默认实现，委托给 ConfigRepository
 */
class DefaultTimeoutSettingsRepository(
    private val configRepo: ConfigRepository
) : TimeoutSettingsRepository {
    
    override fun getRequestTimeout(): Int = configRepo.getRequestTimeout()
    
    override fun setRequestTimeout(seconds: Int) {
        configRepo.setRequestTimeout(seconds)
    }
    
    override fun getSocketTimeout(): Int = configRepo.getSocketTimeout()
    
    override fun setSocketTimeout(seconds: Int) {
        configRepo.setSocketTimeout(seconds)
    }
    
    override fun getConnectTimeout(): Int = configRepo.getConnectTimeout()
    
    override fun setConnectTimeout(seconds: Int) {
        configRepo.setConnectTimeout(seconds)
    }
    
    override fun isStreamResumeEnabled(): Boolean = configRepo.isStreamResumeEnabled()
    
    override fun setStreamResumeEnabled(enabled: Boolean) {
        configRepo.setStreamResumeEnabled(enabled)
    }
    
    override fun getStreamResumeMaxRetries(): Int = configRepo.getStreamResumeMaxRetries()
    
    override fun setStreamResumeMaxRetries(retries: Int) {
        configRepo.setStreamResumeMaxRetries(retries)
    }
}
