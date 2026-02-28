package com.lhzkml.jasmine.core.agent.runtime

import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType
import com.lhzkml.jasmine.core.prompt.llm.ContextManager
import com.lhzkml.jasmine.core.prompt.llm.HistoryCompressionStrategy
import com.lhzkml.jasmine.core.prompt.llm.TokenEstimator

/**
 * 压缩策略构建器
 *
 * 根据 ConfigRepository 中的配置构建 HistoryCompressionStrategy。
 * 将 MainActivity.buildCompressionStrategy() 迁移到 core 层。
 */
object CompressionStrategyBuilder {

    /**
     * 根据配置构建压缩策略
     *
     * @param configRepo 配置仓库
     * @param contextManager 上下文管理器（用于获取 maxTokens 默认值）
     * @return 压缩策略，如果压缩未启用则返回 null
     */
    fun build(configRepo: ConfigRepository, contextManager: ContextManager? = null): HistoryCompressionStrategy? {
        return when (configRepo.getCompressionStrategy()) {
            CompressionStrategyType.TOKEN_BUDGET -> {
                val maxTokens = configRepo.getCompressionMaxTokens()
                val effectiveMaxTokens = if (maxTokens > 0) maxTokens else (contextManager?.maxTokens ?: 128000)
                val threshold = configRepo.getCompressionThreshold() / 100.0
                HistoryCompressionStrategy.TokenBudget(
                    maxTokens = effectiveMaxTokens,
                    threshold = threshold,
                    tokenizer = TokenEstimator
                )
            }
            CompressionStrategyType.WHOLE_HISTORY ->
                HistoryCompressionStrategy.WholeHistory
            CompressionStrategyType.LAST_N -> {
                val n = configRepo.getCompressionLastN()
                HistoryCompressionStrategy.FromLastNMessages(n)
            }
            CompressionStrategyType.CHUNKED -> {
                val size = configRepo.getCompressionChunkSize()
                HistoryCompressionStrategy.Chunked(size)
            }
        }
    }
}
