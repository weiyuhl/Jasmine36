package com.lhzkml.jasmine.core.prompt.llm

/**
 * 压缩策略类型枚举
 * 对应 HistoryCompressionStrategy 的各个子类，用于配置选择。
 *
 * - TOKEN_BUDGET: 基于 token 预算自动触发（推荐）
 * - WHOLE_HISTORY: 整个历史生成 TLDR
 * - LAST_N: 只保留最后 N 条消息生成 TLDR
 * - CHUNKED: 按固定大小分块压缩
 */
enum class CompressionStrategyType {
    TOKEN_BUDGET, WHOLE_HISTORY, LAST_N, CHUNKED
}
