package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * 类型安全的存储键，用于节点间共享数据。
 * @param T 存储值的类型
 * @param name 键名（用于调试和日志）
 */
data class AgentStorageKey<T>(val name: String)
