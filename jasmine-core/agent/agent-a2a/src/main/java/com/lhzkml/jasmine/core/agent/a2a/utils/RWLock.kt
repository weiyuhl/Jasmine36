package com.lhzkml.jasmine.core.agent.a2a.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 读写�?
 * 完整移植 koog �?RWLock
 *
 * 允许并发读访问，但确保独占写访问�?
 * 使用 [Mutex] 协调读者和写者的访问�?
 */
class RWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    /**
     * 在持有读锁的情况下执�?[block]
     */
    suspend fun <T> withReadLock(block: suspend () -> T): T {
        readersCountMutex.withLock {
            if (++readersCount == 1) {
                writeMutex.lock()
            }
        }

        return try {
            block()
        } finally {
            readersCountMutex.withLock {
                if (--readersCount == 0) {
                    writeMutex.unlock()
                }
            }
        }
    }

    /**
     * 在持有写锁的情况下执�?[block]
     */
    suspend fun <T> withWriteLock(block: suspend () -> T): T {
        writeMutex.withLock {
            return block()
        }
    }
}
