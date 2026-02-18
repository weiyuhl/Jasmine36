package com.lhzkml.jasmine.core.agent.tools.a2a.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 键控互斥锁
 * 完整移植 koog 的 KeyedMutex
 *
 * 按键保证互斥，不阻塞线程。
 * API 类似 kotlinx.coroutines Mutex:
 * - lock(key, owner)
 * - tryLock(key, owner)
 * - unlock(key, owner)
 * - withLock(key, owner) { ... }
 *
 * 内部实现：
 * - 每个键持有一个 Mutex 和引用计数（持有者/等待者）。
 * - 在 lock(key) 挂起前递增引用计数，防止等待时条目被移除。
 * - 仅当引用计数为 0 且 mutex 未锁定时才清理条目。
 */
class KeyedMutex<K> {
    private class Entry(
        val mutex: Mutex = Mutex(),
        var refs: Int = 0
    )

    private val mapMutex = Mutex()
    private val entries = mutableMapOf<K, Entry>()

    /**
     * 挂起直到获取 [key] 的锁。
     * 同一协程内对同一键不可重入。
     */
    suspend fun lock(key: K, owner: Any? = null) {
        val entry = mapMutex.withLock {
            val e = entries.getOrPut(key) { Entry() }
            e.refs += 1
            e
        }

        try {
            entry.mutex.lock(owner)
        } catch (t: Throwable) {
            mapMutex.withLock {
                entry.refs -= 1
                if (entry.refs == 0 && !entry.mutex.isLocked && entries[key] == entry) {
                    entries.remove(key)
                }
            }
            throw t
        }
    }

    /**
     * 尝试不挂起地获取 [key] 的锁。
     * 成功返回 true。
     */
    suspend fun tryLock(key: K, owner: Any? = null): Boolean {
        return mapMutex.withLock {
            val existing = entries[key]
            if (existing != null) {
                if (existing.mutex.tryLock(owner)) {
                    existing.refs += 1
                    true
                } else {
                    false
                }
            } else {
                val e = Entry()
                val locked = e.mutex.tryLock(owner)
                if (locked) {
                    e.refs = 1
                    entries[key] = e
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * 释放 [key] 的锁。
     * 每次成功的 lock/tryLock 必须恰好调用一次。
     */
    suspend fun unlock(key: K, owner: Any? = null) {
        val entry = mapMutex.withLock {
            entries[key] ?: throw IllegalStateException("Unlock requested for key without active entry")
        }

        entry.mutex.unlock(owner)

        mapMutex.withLock {
            entry.refs -= 1
            if (entry.refs == 0 && !entry.mutex.isLocked && entries[key] == entry) {
                entries.remove(key)
            }
        }
    }

    /**
     * 观察某个键是否被锁定（仅用于诊断/指标）。
     */
    suspend fun isLocked(key: K): Boolean =
        mapMutex.withLock { entries[key]?.mutex?.isLocked == true }

    /**
     * 检查某个键是否被指定 owner 锁定。
     */
    suspend fun holdsLock(key: K, owner: Any): Boolean =
        mapMutex.withLock { entries[key]?.mutex?.holdsLock(owner) == true }
}

/**
 * 便捷函数，类似 [Mutex.withLock]
 */
suspend inline fun <K, T> KeyedMutex<K>.withLock(
    key: K,
    owner: Any? = null,
    action: suspend () -> T
): T {
    lock(key, owner)
    try {
        return action()
    } finally {
        unlock(key, owner)
    }
}
