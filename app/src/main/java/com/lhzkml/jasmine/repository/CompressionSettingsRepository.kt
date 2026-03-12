package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType

/**
 * 压缩设置 Repository
 *
 * 负责：
 * - Compression 开关
 * - CompressionStrategy
 * - MaxTokens
 * - Threshold
 * - LastN
 * - ChunkSize
 * - KeepRecentRounds
 *
 * 对应页面：
 * - CompressionConfigActivity
 * - ChatExecutor 中上下文压缩策略读取
 */
interface CompressionSettingsRepository {
    
    fun isCompressionEnabled(): Boolean
    fun setCompressionEnabled(enabled: Boolean)
    
    fun getCompressionStrategy(): CompressionStrategyType
    fun setCompressionStrategy(strategy: CompressionStrategyType)
    
    // TOKEN_BUDGET & PROGRESSIVE
    fun getCompressionMaxTokens(): Int
    fun setCompressionMaxTokens(tokens: Int)
    fun getCompressionThreshold(): Int
    fun setCompressionThreshold(threshold: Int)
    
    // LAST_N
    fun getCompressionLastN(): Int
    fun setCompressionLastN(n: Int)
    
    // CHUNKED
    fun getCompressionChunkSize(): Int
    fun setCompressionChunkSize(size: Int)
    
    // PROGRESSIVE
    fun getCompressionKeepRecentRounds(): Int
    fun setCompressionKeepRecentRounds(rounds: Int)
}

class DefaultCompressionSettingsRepository(
    private val configRepo: ConfigRepository
) : CompressionSettingsRepository {
    
    override fun isCompressionEnabled(): Boolean = configRepo.isCompressionEnabled()
    
    override fun setCompressionEnabled(enabled: Boolean) {
        configRepo.setCompressionEnabled(enabled)
    }
    
    override fun getCompressionStrategy(): CompressionStrategyType = 
        configRepo.getCompressionStrategy()
    
    override fun setCompressionStrategy(strategy: CompressionStrategyType) {
        configRepo.setCompressionStrategy(strategy)
    }
    
    override fun getCompressionMaxTokens(): Int = configRepo.getCompressionMaxTokens()
    
    override fun setCompressionMaxTokens(tokens: Int) {
        configRepo.setCompressionMaxTokens(tokens)
    }
    
    override fun getCompressionThreshold(): Int = configRepo.getCompressionThreshold()
    
    override fun setCompressionThreshold(threshold: Int) {
        configRepo.setCompressionThreshold(threshold)
    }
    
    override fun getCompressionLastN(): Int = configRepo.getCompressionLastN()
    
    override fun setCompressionLastN(n: Int) {
        configRepo.setCompressionLastN(n)
    }
    
    override fun getCompressionChunkSize(): Int = configRepo.getCompressionChunkSize()
    
    override fun setCompressionChunkSize(size: Int) {
        configRepo.setCompressionChunkSize(size)
    }
    
    override fun getCompressionKeepRecentRounds(): Int = configRepo.getCompressionKeepRecentRounds()
    
    override fun setCompressionKeepRecentRounds(rounds: Int) {
        configRepo.setCompressionKeepRecentRounds(rounds)
    }
}
