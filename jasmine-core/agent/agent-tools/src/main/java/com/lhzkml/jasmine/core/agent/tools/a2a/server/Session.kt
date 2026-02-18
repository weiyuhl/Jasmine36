package com.lhzkml.jasmine.core.agent.tools.a2a.server

import com.lhzkml.jasmine.core.agent.tools.a2a.model.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/**
 * ID 生成器接口
 * 完整移植 koog 的 IdGenerator
 */
interface IdGenerator {
    fun generateTaskId(message: com.lhzkml.jasmine.core.agent.tools.a2a.model.Message): String
    fun generateContextId(message: com.lhzkml.jasmine.core.agent.tools.a2a.model.Message): String
}

/** UUID ID 生成器 */
object UuidIdGenerator : IdGenerator {
    override fun generateTaskId(message: com.lhzkml.jasmine.core.agent.tools.a2a.model.Message): String =
        java.util.UUID.randomUUID().toString()

    override fun generateContextId(message: com.lhzkml.jasmine.core.agent.tools.a2a.model.Message): String =
        java.util.UUID.randomUUID().toString()
}

/**
 * 懒启动会话
 * 完整移植 koog 的 LazySession
 *
 * 会话在首次访问 agentJob 时启动 Agent 执行。
 */
class LazySession(
    private val coroutineScope: CoroutineScope,
    val eventProcessor: SessionEventProcessor,
    private val agentBlock: suspend () -> Unit
) {
    /** Agent 执行任务 */
    val agentJob: Deferred<Unit> by lazy {
        coroutineScope.async {
            try {
                agentBlock()
            } finally {
                eventProcessor.close()
            }
        }
    }

    /** 事件流 */
    val events: Flow<Event> get() = eventProcessor.events

    /** 等待会话完成 */
    suspend fun join() {
        if ((this::agentJob as Lazy<*>).isInitialized()) {
            try {
                agentJob.await()
            } catch (_: CancellationException) {
                // 忽略取消
            } catch (_: Exception) {
                // 忽略执行异常
            }
        }
    }
}

/**
 * 会话管理器
 * 完整移植 koog 的 SessionManager
 *
 * 管理活跃的 Agent 会话，支持按任务 ID 查找和监控。
 */
class SessionManager(
    private val coroutineScope: CoroutineScope
) {
    private val sessions = mutableMapOf<String, LazySession>()
    private val lock = Any()

    /** 获取指定任务的会话 */
    fun getSession(taskId: String): LazySession? = synchronized(lock) {
        sessions[taskId]
    }

    /**
     * 添加会话并启动监控
     * @return 监控启动的 Job
     */
    fun addSession(session: LazySession): Job = synchronized(lock) {
        sessions[session.eventProcessor.taskId] = session
        // 启动监控：会话完成后自动移除
        coroutineScope.launch {
            try {
                session.agentJob.await()
            } catch (_: Exception) {
                // 忽略
            } finally {
                removeSession(session.eventProcessor.taskId)
            }
        }
    }

    /** 移除会话 */
    fun removeSession(taskId: String) = synchronized(lock) {
        sessions.remove(taskId)
    }
}
