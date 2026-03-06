package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * 节点间共享存储，在整个图执行周期内有效。
 * 每次策略执行创建一个新的 AgentStorage 实例。
 */
class AgentStorage {
    private val store = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: AgentStorageKey<T>): T? = store[key.name] as? T

    fun <T> set(key: AgentStorageKey<T>, value: T) {
        store[key.name] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> remove(key: AgentStorageKey<T>): T? {
        return store.remove(key.name) as? T
    }

    fun clear() = store.clear()
}
