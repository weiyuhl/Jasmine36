package com.lhzkml.jasmine.core.agent.tools.graph

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 类型化存储 key，用于在 AgentStorage 中标识和访问数据。
 * 移植自 koog 的 AIAgentStorageKey。
 *
 * 泛型参数 [T] 指定与此 key 关联的数据类型，确保存取时的类型安全。
 *
 * @param name 唯一标识此 key 的字符串名称
 */
class AgentStorageKey<T : Any>(val name: String) {
    override fun toString(): String = "${super.toString()}(name=$name)"
}

/**
 * 创建指定类型的存储 key。
 * 移植自 koog 的 createStorageKey。
 *
 * @param name key 的名称，用于唯一标识
 * @return 新的 AgentStorageKey 实例
 */
fun <T : Any> createStorageKey(name: String): AgentStorageKey<T> = AgentStorageKey(name)

/**
 * Agent 并发安全的 key-value 存储。
 * 移植自 koog 的 AIAgentStorage。
 *
 * 使用 [createStorageKey] 创建类型化 key，通过 [set] 和 [get] 存取数据。
 */
class AgentStorage {
    private val mutex = Mutex()
    private val storage = mutableMapOf<AgentStorageKey<*>, Any>()

    /**
     * 创建此存储的深拷贝。
     * 移植自 koog 的 AIAgentStorage.copy()。
     *
     * @return 包含相同内容的新 AgentStorage 实例
     */
    internal suspend fun copy(): AgentStorage {
        val newStorage = AgentStorage()
        newStorage.putAll(this.toMap())
        return newStorage
    }

    /**
     * 设置指定 key 关联的值。
     *
     * @param key AgentStorageKey 类型的 key
     * @param value 要关联的值
     */
    suspend fun <T : Any> set(key: AgentStorageKey<T>, value: T): Unit = mutex.withLock {
        storage[key] = value
    }

    /**
     * 获取指定 key 关联的值。
     *
     * @param key AgentStorageKey 类型的 key
     * @return 关联的值，如果 key 不存在则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> get(key: AgentStorageKey<T>): T? = mutex.withLock {
        storage[key] as T?
    }

    /**
     * 获取指定 key 关联的非空值。
     * 如果 key 不存在，抛出 NoSuchElementException。
     *
     * @param key AgentStorageKey 类型的 key
     * @return 关联的值
     * @throws NoSuchElementException 如果 key 不存在
     */
    suspend fun <T : Any> getValue(key: AgentStorageKey<T>): T {
        return get(key) ?: throw NoSuchElementException("Key $key not found in storage")
    }

    /**
     * 移除指定 key 关联的值。
     *
     * @param key AgentStorageKey 类型的 key
     * @return 被移除的值，如果 key 不存在则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> remove(key: AgentStorageKey<T>): T? = mutex.withLock {
        storage.remove(key) as T?
    }

    /**
     * 将存储转换为 Map 快照。
     *
     * @return 包含所有 key-value 对的 Map
     */
    suspend fun toMap(): Map<AgentStorageKey<*>, Any> = mutex.withLock {
        storage.toMap()
    }

    /**
     * 批量添加 key-value 对。
     *
     * @param map 要添加的 key-value 对
     */
    suspend fun putAll(map: Map<AgentStorageKey<*>, Any>): Unit = mutex.withLock {
        storage.putAll(map)
    }

    /**
     * 清空所有数据。
     */
    suspend fun clear(): Unit = mutex.withLock {
        storage.clear()
    }
}
