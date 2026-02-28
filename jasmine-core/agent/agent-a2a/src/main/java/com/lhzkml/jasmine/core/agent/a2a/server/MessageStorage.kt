package com.lhzkml.jasmine.core.agent.a2a.server

import com.lhzkml.jasmine.core.agent.a2a.MessageOperationException
import com.lhzkml.jasmine.core.agent.a2a.model.Message
import com.lhzkml.jasmine.core.agent.a2a.utils.RWLock

/**
 * 消息存储接口
 * 完整移植 koog �?MessageStorage
 *
 * 存储与特定上下文关联的消息。可用于跟踪对话历史�?
 * 实现必须保证并发安全�?
 */
interface MessageStorage {
    /**
     * 保存消息到存储�?
     *
     * @param message 要保存的消息（消息必须包�?contextId�?
     * @throws MessageOperationException 如果消息无法保存
     */
    suspend fun save(message: Message)

    /**
     * 获取指定上下文的所有消息�?
     *
     * @param contextId 上下文标识符
     */
    suspend fun getByContext(contextId: String): List<Message>

    /**
     * 删除指定上下文的所有消息�?
     *
     * @param contextId 上下文标识符
     * @throws MessageOperationException 如果某些消息无法删除
     */
    suspend fun deleteByContext(contextId: String)

    /**
     * 替换指定上下文的所有消息�?
     *
     * @param contextId 上下文标识符
     * @param messages 替换的消息列�?
     * @throws MessageOperationException 如果上下文无法替�?
     */
    suspend fun replaceByContext(contextId: String, messages: List<Message>)
}

/**
 * 内存消息存储
 * 完整移植 koog �?InMemoryMessageStorage
 *
 * 使用线程安全�?map 在内存中存储消息�?
 * 按上下文 ID 分组，通过读写锁保证并发安全�?
 */
class InMemoryMessageStorage : MessageStorage {
    private val messagesByContext = mutableMapOf<String, MutableList<Message>>()
    private val rwLock = RWLock()

    override suspend fun save(message: Message): Unit = rwLock.withWriteLock {
        val contextId = message.contextId
            ?: throw MessageOperationException("Message must have a contextId to be saved")

        messagesByContext.getOrPut(contextId) { mutableListOf() }.add(message)
    }

    override suspend fun getByContext(contextId: String): List<Message> = rwLock.withReadLock {
        messagesByContext[contextId]?.toList() ?: emptyList()
    }

    override suspend fun deleteByContext(contextId: String): Unit = rwLock.withWriteLock {
        messagesByContext -= contextId
    }

    override suspend fun replaceByContext(contextId: String, messages: List<Message>): Unit = rwLock.withWriteLock {
        // 验证所有消息都有正确的 contextId
        val invalidMessages = messages.filter { it.contextId != contextId }
        if (invalidMessages.isNotEmpty()) {
            throw MessageOperationException(
                "All messages must have contextId '$contextId', but found messages with different contextIds: " +
                    invalidMessages.map { it.contextId }.distinct().joinToString()
            )
        }

        messagesByContext[contextId] = messages.toMutableList()
    }
}

/**
 * 上下文限定的消息存储
 * 完整移植 koog �?ContextMessageStorage
 *
 * 包装 [MessageStorage]，提供便捷方法和上下�?ID 验证�?
 *
 * @param contextId 上下文标识符
 * @param messageStorage 底层 [MessageStorage] 实现
 */
class ContextMessageStorage(
    private val contextId: String,
    private val messageStorage: MessageStorage
) {
    /**
     * 保存消息到存储�?
     *
     * @param message 要保存的消息
     */
    suspend fun save(message: Message) {
        require(message.contextId == contextId) {
            "contextId of message must be same as current contextId"
        }
        messageStorage.save(message)
    }

    /**
     * 获取当前上下文的所有消息�?
     */
    suspend fun getAll(): List<Message> {
        return messageStorage.getByContext(contextId)
    }

    /**
     * 删除当前上下文的所有消息�?
     */
    suspend fun deleteAll() {
        messageStorage.deleteByContext(contextId)
    }

    /**
     * 替换当前上下文的所有消息�?
     *
     * @param messages 替换的消息列�?
     */
    suspend fun replaceAll(messages: List<Message>) {
        require(messages.all { it.contextId == contextId }) {
            "contextId of messages must be same as current contextId"
        }
        messageStorage.replaceByContext(contextId, messages)
    }
}
