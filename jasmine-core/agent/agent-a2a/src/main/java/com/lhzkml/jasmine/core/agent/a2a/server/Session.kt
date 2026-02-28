package com.lhzkml.jasmine.core.agent.a2a.server

import com.lhzkml.jasmine.core.agent.a2a.model.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * ID 生成器接�?
 * 完整移植 koog �?IdGenerator
 */
interface IdGenerator {
    fun generateTaskId(message: com.lhzkml.jasmine.core.agent.a2a.model.Message): String
    fun generateContextId(message: com.lhzkml.jasmine.core.agent.a2a.model.Message): String
}

/** UUID ID 生成器 */
object UuidIdGenerator : IdGenerator {
    override fun generateTaskId(message: com.lhzkml.jasmine.core.agent.a2a.model.Message): String =
        java.util.UUID.randomUUID().toString()

    override fun generateContextId(message: com.lhzkml.jasmine.core.agent.a2a.model.Message): String =
        java.util.UUID.randomUUID().toString()
}

/**
 * 会话
 * 完整移植 koog �?Session
 *
 * 表示一个具有生命周期管理的会话�?
 *
 * @property eventProcessor 会话事件处理�?
 * @property agentJob 与此会话执行关联的执行进�?
 */
class Session(
    val eventProcessor: SessionEventProcessor,
    val agentJob: Deferred<Unit>
) {
    /** 与此会话关联的上下文 ID */
    val contextId: String = eventProcessor.contextId

    /** 与此会话关联的任�?ID */
    val taskId: String = eventProcessor.taskId

    /** 与此会话关联的事件流 */
    val events: Flow<Event> = eventProcessor.events

    /**
     * 启动 [agentJob]（如果尚未启动）�?
     */
    fun start() {
        agentJob.start()
    }

    /**
     * 挂起直到会话（即事件流和 agent job）完成�?
     * 先等待事件流完成，以避免过早触发 agent job�?
     * 假设事件流完成时，agent job 已经完成或被取消�?
     */
    suspend fun join() {
        events.collect()
        agentJob.join()
    }

    /**
     * [start] 然后 [join] 会话�?
     */
    suspend fun startAndJoin() {
        start()
        join()
    }

    /**
     * 取消执行进程，等待其完成，然后关闭事件处理器�?
     */
    suspend fun cancelAndJoin() {
        agentJob.cancelAndJoin()
        eventProcessor.close()
    }
}

/**
 * 创建一个懒启动�?[Session] 实例
 * 完整移植 koog �?LazySession 工厂函数
 *
 * @param coroutineScope 用于运行 [block] 的协程作用域
 * @param eventProcessor 会话事件处理�?
 * @param block 要执行的代码�?
 */
@Suppress("FunctionName")
fun LazySession(
    coroutineScope: CoroutineScope,
    eventProcessor: SessionEventProcessor,
    block: suspend CoroutineScope.() -> Unit
): Session {
    return Session(
        eventProcessor = eventProcessor,
        agentJob = coroutineScope.async(start = CoroutineStart.LAZY, block = block)
    )
}
