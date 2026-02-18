package com.lhzkml.jasmine.core.agent.tools.a2a.server

import com.lhzkml.jasmine.core.agent.tools.a2a.model.Message

/**
 * 消息存储接口
 * 完整移植 koog 的 MessageStorage
 */
interface MessageStorage {
    suspend fun save(contextId: String, message: Message)
    suspend fun getByContext(contextId: String): List<Message>
    suspend fun deleteByContext(contextId: String)
}

/**
 * 内存消息存储
 * 完整移植 koog 的 InMemoryMessageStorage
 */
class InMemoryMessageStorage : MessageStorage {
    private val messages = mutableMapOf<String, MutableList<Message>>()
    private val lock = Any()

    override suspend fun save(contextId: String, message: Message): Unit = synchronized(lock) {
        messages.getOrPut(contextId) { mutableListOf() }.add(message)
    }

    override suspend fun getByContext(contextId: String): List<Message> = synchronized(lock) {
        messages[contextId]?.toList() ?: emptyList()
    }

    override suspend fun deleteByContext(contextId: String): Unit = synchronized(lock) {
        messages.remove(contextId)
    }
}

/**
 * 上下文限定的消息存储
 * 完整移植 koog 的 ContextMessageStorage
 */
class ContextMessageStorage(
    private val contextId: String,
    private val messageStorage: MessageStorage
) {
    suspend fun save(message: Message) = messageStorage.save(contextId, message)
    suspend fun getAll(): List<Message> = messageStorage.getByContext(contextId)
    suspend fun deleteAll() = messageStorage.deleteByContext(contextId)
}
