package com.lhzkml.jasmine.core.agent.tools.a2a.server

import com.lhzkml.jasmine.core.agent.tools.a2a.model.TaskEvent
import com.lhzkml.jasmine.core.agent.tools.a2a.server.notifications.PushNotificationConfigStorage
import com.lhzkml.jasmine.core.agent.tools.a2a.server.notifications.PushNotificationSender
import com.lhzkml.jasmine.core.agent.tools.a2a.utils.KeyedMutex
import com.lhzkml.jasmine.core.agent.tools.a2a.utils.RWLock
import com.lhzkml.jasmine.core.agent.tools.a2a.utils.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart

/**
 * 会话管理器
 * 完整移植 koog 的 SessionManager
 *
 * 管理活跃的 [Session] 实例集合，在每个会话完成后发送推送通知（如果已配置）。
 * 当 agent job 完成时（无论成功与否），自动关闭并移除会话。
 *
 * 此外，如果配置了推送通知，在每个任务会话完成后，
 * 会发送包含当前任务状态的推送通知。
 *
 * 提供锁定任务 ID 的能力。
 *
 * @param coroutineScope 监控 job 将在其中启动的作用域
 * @param tasksMutex 用于锁定特定任务 ID 的互斥锁
 * @param cancelKey 取消键生成函数
 * @param taskStorage 任务存储
 * @param pushConfigStorage 推送通知配置存储
 * @param pushSender 推送通知发送器
 */
class SessionManager(
    private val coroutineScope: CoroutineScope,
    private val tasksMutex: KeyedMutex<String>,
    private val cancelKey: (String) -> String,
    private val taskStorage: TaskStorage,
    private val pushConfigStorage: PushNotificationConfigStorage? = null,
    private val pushSender: PushNotificationSender? = null
) {
    /** 任务 ID 到会话的映射。所有会话都有关联的任务 ID，即使任务不会被创建。 */
    private val sessions = mutableMapOf<String, Session>()
    private val sessionsRwLock = RWLock()

    /**
     * 添加会话到活跃会话集合。
     * 当会话完成时（无论成功与否），处理清理、关闭和移除会话。
     * 如果配置了推送通知，在每个会话完成后发送推送通知。
     *
     * @param session 要添加的会话
     * @return 一个 [CompletableJob]，指示监控协程何时启动并准备好监控会话。
     *         在此 job 完成后才启动 agent 执行至关重要，以确保监控不会跳过任何事件。
     * @throws IllegalStateException 如果同一任务 ID 的会话已存在
     */
    suspend fun addSession(session: Session): CompletableJob {
        sessionsRwLock.withWriteLock {
            check(session.taskId !in sessions) {
                "Session for taskId '${session.taskId}' already runs."
            }
            sessions[session.taskId] = session
        }

        // 指示监控已启动的信号
        val monitoringStarted = Job()

        // 监控 agent job 完成，发送推送通知并从 map 中移除会话
        coroutineScope.launch {
            val firstEvent = session.events
                .onStart { monitoringStarted.complete() }
                .firstOrNull()

            // 等待 agent job 完成
            session.agentJob.join()

            /*
             检查并等待是否有正在运行的取消请求仍在发布事件。
             然后从会话 map 中移除。
             */
            tasksMutex.withLock(cancelKey(session.taskId)) {
                sessionsRwLock.withWriteLock {
                    sessions -= session.taskId
                    session.cancelAndJoin()
                }
            }

            // 会话完成后，如果配置了推送通知，发送包含当前任务状态的推送通知
            coroutineScope.launch {
                if (firstEvent is TaskEvent && pushSender != null && pushConfigStorage != null) {
                    val task = taskStorage.get(session.taskId, historyLength = 0, includeArtifacts = false)

                    if (task != null) {
                        pushConfigStorage.getAll(session.taskId).forEach { config ->
                            try {
                                pushSender.send(config, task)
                            } catch (e: Exception) {
                                System.err.println(
                                    "Failed to send push notification: taskId='${session.taskId}' error=${e.message}"
                                )
                            }
                        }
                    }
                }
            }
        }

        return monitoringStarted
    }

    /**
     * 获取指定任务 ID 的会话（如果存在）。
     */
    suspend fun getSession(taskId: String): Session? = sessionsRwLock.withReadLock {
        sessions[taskId]
    }

    /**
     * 返回活跃会话数量。
     */
    suspend fun activeSessions(): Int = sessionsRwLock.withReadLock {
        sessions.size
    }
}
